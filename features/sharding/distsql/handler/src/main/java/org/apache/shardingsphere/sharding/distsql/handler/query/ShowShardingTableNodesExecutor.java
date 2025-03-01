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

package org.apache.shardingsphere.sharding.distsql.handler.query;

import lombok.Setter;
import org.apache.shardingsphere.distsql.handler.type.rql.aware.DatabaseRuleAwareRQLExecutor;
import org.apache.shardingsphere.infra.datanode.DataNode;
import org.apache.shardingsphere.infra.merge.result.impl.local.LocalDataQueryResultRow;
import org.apache.shardingsphere.sharding.distsql.statement.ShowShardingTableNodesStatement;
import org.apache.shardingsphere.sharding.rule.ShardingRule;
import org.apache.shardingsphere.sharding.rule.TableRule;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

/**
 * Show sharding table nodes executor.
 */
@Setter
public final class ShowShardingTableNodesExecutor implements DatabaseRuleAwareRQLExecutor<ShowShardingTableNodesStatement, ShardingRule> {
    
    private ShardingRule rule;
    
    @Override
    public Collection<String> getColumnNames() {
        return Arrays.asList("name", "nodes");
    }
    
    @Override
    public Collection<LocalDataQueryResultRow> getRows(final ShowShardingTableNodesStatement sqlStatement) {
        String tableName = sqlStatement.getTableName();
        return null == tableName
                ? rule.getTableRules().entrySet().stream().map(entry -> new LocalDataQueryResultRow(entry.getKey(), getTableNodes(entry.getValue()))).collect(Collectors.toList())
                : Collections.singleton(new LocalDataQueryResultRow(tableName, getTableNodes(rule.getTableRule(tableName))));
    }
    
    private String getTableNodes(final TableRule tableRule) {
        return tableRule.getActualDataNodes().stream().map(DataNode::format).collect(Collectors.joining(", "));
    }
    
    @Override
    public Class<ShardingRule> getRuleClass() {
        return ShardingRule.class;
    }
    
    @Override
    public Class<ShowShardingTableNodesStatement> getType() {
        return ShowShardingTableNodesStatement.class;
    }
}
