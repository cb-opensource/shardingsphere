/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.data.pipeline.core.importer.sink.type;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.data.pipeline.core.constant.PipelineSQLOperationType;
import org.apache.shardingsphere.data.pipeline.core.datasource.PipelineDataSourceManager;
import org.apache.shardingsphere.data.pipeline.core.exception.job.PipelineImporterJobWriteException;
import org.apache.shardingsphere.data.pipeline.core.importer.ImporterConfiguration;
import org.apache.shardingsphere.data.pipeline.core.importer.sink.PipelineSink;
import org.apache.shardingsphere.data.pipeline.core.ingest.record.Column;
import org.apache.shardingsphere.data.pipeline.core.ingest.record.DataRecord;
import org.apache.shardingsphere.data.pipeline.core.ingest.record.Record;
import org.apache.shardingsphere.data.pipeline.core.ingest.record.RecordUtils;
import org.apache.shardingsphere.data.pipeline.core.ingest.record.group.DataRecordGroupEngine;
import org.apache.shardingsphere.data.pipeline.core.ingest.record.group.GroupedDataRecord;
import org.apache.shardingsphere.data.pipeline.core.job.progress.listener.PipelineJobProgressUpdatedParameter;
import org.apache.shardingsphere.data.pipeline.core.sqlbuilder.sql.PipelineImportSQLBuilder;
import org.apache.shardingsphere.data.pipeline.core.util.PipelineJdbcUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Pipeline data source sink.
 */
@Slf4j
public final class PipelineDataSourceSink implements PipelineSink {
    
    private final ImporterConfiguration importerConfig;
    
    private final DataSource dataSource;
    
    private final PipelineImportSQLBuilder importSQLBuilder;
    
    private final DataRecordGroupEngine groupEngine;
    
    private final AtomicReference<PreparedStatement> runningStatement;
    
    public PipelineDataSourceSink(final ImporterConfiguration importerConfig, final PipelineDataSourceManager dataSourceManager) {
        this.importerConfig = importerConfig;
        dataSource = dataSourceManager.getDataSource(importerConfig.getDataSourceConfig());
        importSQLBuilder = new PipelineImportSQLBuilder(importerConfig.getDataSourceConfig().getDatabaseType());
        groupEngine = new DataRecordGroupEngine();
        runningStatement = new AtomicReference<>();
    }
    
    @Override
    public PipelineJobProgressUpdatedParameter write(final String ackId, final Collection<Record> records) {
        List<DataRecord> dataRecords = records.stream().filter(DataRecord.class::isInstance).map(DataRecord.class::cast).collect(Collectors.toList());
        if (dataRecords.isEmpty()) {
            return new PipelineJobProgressUpdatedParameter(0);
        }
        for (GroupedDataRecord each : groupEngine.group(dataRecords)) {
            batchWrite(each.getDeleteDataRecords());
            batchWrite(each.getInsertDataRecords());
            batchWrite(each.getUpdateDataRecords());
        }
        return new PipelineJobProgressUpdatedParameter((int) dataRecords.stream().filter(each -> PipelineSQLOperationType.INSERT == each.getType()).count());
    }
    
    @SuppressWarnings("BusyWait")
    @SneakyThrows(InterruptedException.class)
    private void batchWrite(final Collection<DataRecord> records) {
        if (records.isEmpty()) {
            return;
        }
        for (int i = 0; !Thread.interrupted() && i <= importerConfig.getRetryTimes(); i++) {
            try {
                doWrite(records);
                break;
            } catch (final SQLException ex) {
                log.error("Flush failed {}/{} times.", i, importerConfig.getRetryTimes(), ex);
                if (i == importerConfig.getRetryTimes()) {
                    throw new PipelineImporterJobWriteException(ex);
                }
                Thread.sleep(Math.min(5 * 60 * 1000L, 1000L << i));
            }
        }
    }
    
    private void doWrite(final Collection<DataRecord> records) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            boolean enableTransaction = records.size() > 1;
            if (enableTransaction) {
                connection.setAutoCommit(false);
            }
            switch (records.iterator().next().getType()) {
                case INSERT:
                    Optional.ofNullable(importerConfig.getRateLimitAlgorithm()).ifPresent(optional -> optional.intercept(PipelineSQLOperationType.INSERT, 1));
                    executeBatchInsert(connection, records);
                    break;
                case UPDATE:
                    Optional.ofNullable(importerConfig.getRateLimitAlgorithm()).ifPresent(optional -> optional.intercept(PipelineSQLOperationType.UPDATE, 1));
                    executeUpdate(connection, records);
                    break;
                case DELETE:
                    Optional.ofNullable(importerConfig.getRateLimitAlgorithm()).ifPresent(optional -> optional.intercept(PipelineSQLOperationType.DELETE, 1));
                    executeBatchDelete(connection, records);
                    break;
                default:
                    break;
            }
            if (enableTransaction) {
                connection.commit();
            }
        }
    }
    
    private void executeBatchInsert(final Connection connection, final Collection<DataRecord> dataRecords) throws SQLException {
        DataRecord dataRecord = dataRecords.iterator().next();
        String sql = importSQLBuilder.buildInsertSQL(importerConfig.findSchemaName(dataRecord.getTableName()).orElse(null), dataRecord);
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            runningStatement.set(preparedStatement);
            preparedStatement.setQueryTimeout(30);
            for (DataRecord each : dataRecords) {
                for (int i = 0; i < each.getColumnCount(); i++) {
                    preparedStatement.setObject(i + 1, each.getColumn(i).getValue());
                }
                preparedStatement.addBatch();
            }
            preparedStatement.executeBatch();
        } finally {
            runningStatement.set(null);
        }
    }
    
    private void executeUpdate(final Connection connection, final Collection<DataRecord> dataRecords) throws SQLException {
        for (DataRecord each : dataRecords) {
            executeUpdate(connection, each);
        }
    }
    
    private void executeUpdate(final Connection connection, final DataRecord dataRecord) throws SQLException {
        Set<String> shardingColumns = importerConfig.getShardingColumns(dataRecord.getTableName());
        List<Column> conditionColumns = RecordUtils.extractConditionColumns(dataRecord, shardingColumns);
        List<Column> setColumns = dataRecord.getColumns().stream().filter(Column::isUpdated).collect(Collectors.toList());
        String sql = importSQLBuilder.buildUpdateSQL(importerConfig.findSchemaName(dataRecord.getTableName()).orElse(null), dataRecord, conditionColumns);
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            runningStatement.set(preparedStatement);
            for (int i = 0; i < setColumns.size(); i++) {
                preparedStatement.setObject(i + 1, setColumns.get(i).getValue());
            }
            for (int i = 0; i < conditionColumns.size(); i++) {
                Column keyColumn = conditionColumns.get(i);
                // TODO There to be compatible with PostgreSQL before value is null except primary key and unsupported updating sharding value now.
                if (shardingColumns.contains(keyColumn.getName()) && null == keyColumn.getOldValue()) {
                    preparedStatement.setObject(setColumns.size() + i + 1, keyColumn.getValue());
                    continue;
                }
                preparedStatement.setObject(setColumns.size() + i + 1, keyColumn.getOldValue());
            }
            // TODO if table without unique key the conditionColumns before values is null, so update will fail at PostgreSQL
            int updateCount = preparedStatement.executeUpdate();
            if (1 != updateCount) {
                log.warn("executeUpdate failed, updateCount={}, updateSql={}, updatedColumns={}, conditionColumns={}", updateCount, sql, setColumns, conditionColumns);
            }
        } finally {
            runningStatement.set(null);
        }
    }
    
    private void executeBatchDelete(final Connection connection, final Collection<DataRecord> dataRecords) throws SQLException {
        DataRecord dataRecord = dataRecords.iterator().next();
        String sql = importSQLBuilder.buildDeleteSQL(importerConfig.findSchemaName(dataRecord.getTableName()).orElse(null), dataRecord,
                RecordUtils.extractConditionColumns(dataRecord, importerConfig.getShardingColumns(dataRecord.getTableName())));
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            runningStatement.set(preparedStatement);
            preparedStatement.setQueryTimeout(30);
            for (DataRecord each : dataRecords) {
                List<Column> conditionColumns = RecordUtils.extractConditionColumns(each, importerConfig.getShardingColumns(dataRecord.getTableName()));
                for (int i = 0; i < conditionColumns.size(); i++) {
                    Object oldValue = conditionColumns.get(i).getOldValue();
                    if (null == oldValue) {
                        log.warn("Record old value is null, record={}", each);
                    }
                    preparedStatement.setObject(i + 1, oldValue);
                }
                preparedStatement.addBatch();
            }
            preparedStatement.executeBatch();
        } finally {
            runningStatement.set(null);
        }
    }
    
    @Override
    public void close() {
        Optional.ofNullable(runningStatement.get()).ifPresent(PipelineJdbcUtils::cancelStatement);
    }
}
