/*
 * Copyright 2016-2018 shardingsphere.io.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * </p>
 */

package io.shardingsphere.jdbc.orchestration.internal;

import com.google.common.eventbus.Subscribe;
import io.shardingsphere.core.api.config.MasterSlaveRuleConfiguration;
import io.shardingsphere.core.api.config.ShardingRuleConfiguration;
import io.shardingsphere.core.constant.ConnectionMode;
import io.shardingsphere.core.constant.properties.ShardingProperties;
import io.shardingsphere.core.constant.properties.ShardingPropertiesConstant;
import io.shardingsphere.core.event.orche.config.ShardingConfigurationEventBusEvent;
import io.shardingsphere.core.event.orche.state.CircuitStateEventBusEvent;
import io.shardingsphere.core.event.orche.state.DisabledStateEventBusEvent;
import io.shardingsphere.core.executor.ShardingExecuteEngine;
import io.shardingsphere.core.jdbc.adapter.AbstractDataSourceAdapter;
import io.shardingsphere.core.jdbc.core.connection.ShardingConnection;
import io.shardingsphere.core.jdbc.core.datasource.MasterSlaveDataSource;
import io.shardingsphere.core.jdbc.core.datasource.ShardingDataSource;
import io.shardingsphere.core.orche.datasource.CircuitBreakerDataSource;
import io.shardingsphere.core.rule.MasterSlaveRule;
import io.shardingsphere.core.rule.ShardingRule;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Properties;

/**
 * Orchestration sharding datasource.
 *
 * @author caohao
 */
@Slf4j
public final class OrchestrationShardingDataSource extends AbstractDataSourceAdapter implements AutoCloseable {
    
    private final ShardingDataSource dataSource;
    
    private final OrchestrationFacade orchestrationFacade;
    
    private Collection<String> disabledDataSourceNames = new LinkedList<>();
    
    private boolean isCircuitBreak;
    
    public OrchestrationShardingDataSource(final Map<String, DataSource> dataSourceMap, final ShardingRuleConfiguration shardingRuleConfig,
                                           final Map<String, Object> configMap, final Properties props, final OrchestrationFacade orchestrationFacade) throws SQLException {
        super(dataSourceMap.values());
        this.dataSource = new ShardingDataSource(dataSourceMap, new ShardingRule(shardingRuleConfig, dataSourceMap.keySet()), configMap, props);
        this.orchestrationFacade = orchestrationFacade;
        this.orchestrationFacade.init(dataSourceMap, shardingRuleConfig, configMap, props);
    }
    
    @Override
    public ShardingConnection getConnection() {
        return dataSource.getConnection();
    }
    
    @Override
    public void close() {
        dataSource.close();
        orchestrationFacade.close();
    }
    
//    private Map<String, DataSource> getRawDataSourceMap(final Map<String, DataSource> dataSourceMap) {
//        Map<String, DataSource> result = new LinkedHashMap<>();
//        if (null == dataSourceMap) {
//            return result;
//        }
//        for (Map.Entry<String, DataSource> entry : dataSourceMap.entrySet()) {
//            String dataSourceName = entry.getKey();
//            DataSource dataSource = entry.getValue();
//            if (dataSource instanceof MasterSlaveDataSource) {
//                result.putAll(((MasterSlaveDataSource) dataSource).getAllDataSources());
//            } else {
//                result.put(dataSourceName, dataSource);
//            }
//        }
//        return result;
//    }
//
//    private ShardingRuleConfiguration getShardingRuleConfiguration(final Map<String, DataSource> dataSourceMap, final ShardingRuleConfiguration shardingRuleConfig) {
//        Collection<MasterSlaveRuleConfiguration> masterSlaveRuleConfigs = new LinkedList<>();
//        if (null == dataSourceMap || !shardingRuleConfig.getMasterSlaveRuleConfigs().isEmpty()) {
//            return shardingRuleConfig;
//        }
//        for (DataSource each : dataSourceMap.values()) {
//            if (!(each instanceof MasterSlaveDataSource)) {
//                continue;
//            }
//            MasterSlaveRule masterSlaveRule = ((MasterSlaveDataSource) each).getMasterSlaveRule();
//            masterSlaveRuleConfigs.add(new MasterSlaveRuleConfiguration(
//                    masterSlaveRule.getName(), masterSlaveRule.getMasterDataSourceName(), masterSlaveRule.getSlaveDataSourceNames(), masterSlaveRule.getLoadBalanceAlgorithm()));
//        }
//        shardingRuleConfig.setMasterSlaveRuleConfigs(masterSlaveRuleConfigs);
//        return shardingRuleConfig;
//    }
    
    /**
     * Renew disable dataSource names.
     *
     * @param disabledStateEventBusEvent jdbc disabled event bus event
     */
    @Subscribe
    public void renewDisabledDataSourceNames(final DisabledStateEventBusEvent disabledStateEventBusEvent) {
        disabledDataSourceNames = disabledStateEventBusEvent.getDisabledDataSourceNames();
    }
    
    /**
     * Renew circuit breaker dataSource names.
     *
     * @param circuitStateEventBusEvent jdbc disabled event bus event
     */
    @Subscribe
    public void renewCircuitBreakerDataSourceNames(final CircuitStateEventBusEvent circuitStateEventBusEvent) {
        isCircuitBreak = circuitStateEventBusEvent.isCircuitBreak();
    }
    
    /**
     * Get available data source map.
     *
     * @return available data source map
     */
    public Map<String, DataSource> getDataSourceMap() {
        if (isCircuitBreak) {
            return getCircuitBreakerDataSourceMap();
        }
        if (!disabledDataSourceNames.isEmpty()) {
            return getAvailableDataSourceMap();
        }
        return dataSource.getShardingContext().getDataSourceMap();
    }
    
    private Map<String, DataSource> getAvailableDataSourceMap() {
        Map<String, DataSource> result = new LinkedHashMap<>(dataSourceMap);
        for (String each : disabledDataSourceNames) {
            result.remove(each);
        }
        return result;
    }
    
    private Map<String, DataSource> getCircuitBreakerDataSourceMap() {
        Map<String, DataSource> result = new LinkedHashMap<>();
        for (String each : dataSourceMap.keySet()) {
            result.put(each, new CircuitBreakerDataSource());
        }
        return result;
    }
    
    /**
     * Renew sharding data source.
     *
     * @param shardingEvent sharding configuration event bus event.
     */
    @Subscribe
    public void renew(final ShardingConfigurationEventBusEvent shardingEvent) {
        super.renew(shardingEvent.getDataSourceMap().values());
        shardingProperties = new ShardingProperties(null == shardingEvent.getProps() ? new Properties() : shardingEvent.getProps());
        int newExecutorSize = shardingProperties.getValue(ShardingPropertiesConstant.EXECUTOR_SIZE);
        boolean newShowSQL = shardingProperties.getValue(ShardingPropertiesConstant.SQL_SHOW);
        ShardingExecuteEngine newExecuteEngine = new ShardingExecuteEngine(newExecutorSize);
        ConnectionMode newConnectionMode = ConnectionMode.valueOf(shardingProperties.<String>getValue(ShardingPropertiesConstant.CONNECTION_MODE));
        shardingContext.renew(shardingEvent.getDataSourceMap(), shardingEvent.getShardingRule(), getDatabaseType(), newExecuteEngine, newConnectionMode, newShowSQL);
    }
}
