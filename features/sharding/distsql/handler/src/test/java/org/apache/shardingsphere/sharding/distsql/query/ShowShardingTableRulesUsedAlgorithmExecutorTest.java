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

package org.apache.shardingsphere.sharding.distsql.query;

import org.apache.shardingsphere.infra.config.algorithm.AlgorithmConfiguration;
import org.apache.shardingsphere.infra.merge.result.impl.local.LocalDataQueryResultRow;
import org.apache.shardingsphere.sharding.api.config.ShardingRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.rule.ShardingAutoTableRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.rule.ShardingTableRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.strategy.keygen.KeyGenerateStrategyConfiguration;
import org.apache.shardingsphere.sharding.api.config.strategy.sharding.NoneShardingStrategyConfiguration;
import org.apache.shardingsphere.sharding.api.config.strategy.sharding.StandardShardingStrategyConfiguration;
import org.apache.shardingsphere.sharding.distsql.handler.query.ShowShardingTableRulesUsedAlgorithmExecutor;
import org.apache.shardingsphere.sharding.distsql.statement.ShowShardingTableRulesUsedAlgorithmStatement;
import org.apache.shardingsphere.sharding.rule.ShardingRule;
import org.apache.shardingsphere.test.util.PropertiesBuilder;
import org.apache.shardingsphere.test.util.PropertiesBuilder.Property;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Iterator;
import java.util.Optional;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShowShardingTableRulesUsedAlgorithmExecutorTest {
    
    @Test
    void assertGetRowData() {
        ShardingRule rule = mock(ShardingRule.class);
        when(rule.getConfiguration()).thenReturn(createRuleConfiguration());
        ShowShardingTableRulesUsedAlgorithmExecutor executor = new ShowShardingTableRulesUsedAlgorithmExecutor();
        executor.setRule(rule);
        ShowShardingTableRulesUsedAlgorithmStatement statement = mock(ShowShardingTableRulesUsedAlgorithmStatement.class);
        when(statement.getShardingAlgorithmName()).thenReturn(Optional.of("t_order_inline"));
        Collection<LocalDataQueryResultRow> actual = executor.getRows(statement);
        assertThat(actual.size(), is(1));
        Iterator<LocalDataQueryResultRow> iterator = actual.iterator();
        LocalDataQueryResultRow row = iterator.next();
        assertThat(row.getCell(1), is("table"));
        assertThat(row.getCell(2), is("t_order"));
        when(statement.getShardingAlgorithmName()).thenReturn(Optional.of("auto_mod"));
        actual = executor.getRows(statement);
        assertThat(actual.size(), is(1));
        iterator = actual.iterator();
        row = iterator.next();
        assertThat(row.getCell(1), is("auto_table"));
        assertThat(row.getCell(2), is("t_order_auto"));
    }
    
    private ShardingRuleConfiguration createRuleConfiguration() {
        ShardingRuleConfiguration result = new ShardingRuleConfiguration();
        result.getTables().add(createShardingTableRuleConfiguration());
        result.getAutoTables().add(createShardingAutoTableRuleConfiguration());
        result.setDefaultDatabaseShardingStrategy(new StandardShardingStrategyConfiguration("user_id", "database_inline"));
        result.setDefaultTableShardingStrategy(new NoneShardingStrategyConfiguration());
        result.getShardingAlgorithms().put("database_inline", createShardingInlineAlgorithmConfiguration("ds_${user_id % 2}"));
        result.getShardingAlgorithms().put("t_order_inline", createShardingInlineAlgorithmConfiguration("t_order_${order_id % 2}"));
        result.getShardingAlgorithms().put("auto_mod", createShardingAutoModAlgorithmConfiguration());
        result.getKeyGenerators().put("snowflake", createKeyGeneratorConfiguration());
        return result;
    }
    
    private ShardingAutoTableRuleConfiguration createShardingAutoTableRuleConfiguration() {
        ShardingAutoTableRuleConfiguration result = new ShardingAutoTableRuleConfiguration("t_order_auto", "ds_0, ds_1");
        result.setShardingStrategy(new StandardShardingStrategyConfiguration("order_id", "auto_mod"));
        result.setKeyGenerateStrategy(new KeyGenerateStrategyConfiguration("order_id", "snowflake"));
        return result;
    }
    
    private ShardingTableRuleConfiguration createShardingTableRuleConfiguration() {
        ShardingTableRuleConfiguration result = new ShardingTableRuleConfiguration("t_order", "ds_${0..1}.t_order_${0..1}");
        result.setTableShardingStrategy(new StandardShardingStrategyConfiguration("order_id", "t_order_inline"));
        result.setKeyGenerateStrategy(new KeyGenerateStrategyConfiguration("order_id", "snowflake"));
        return result;
    }
    
    private AlgorithmConfiguration createShardingInlineAlgorithmConfiguration(final String algorithmExpression) {
        return new AlgorithmConfiguration("INLINE", PropertiesBuilder.build(new Property("algorithm-expression", algorithmExpression)));
    }
    
    private AlgorithmConfiguration createShardingAutoModAlgorithmConfiguration() {
        return new AlgorithmConfiguration("MOD", PropertiesBuilder.build(new Property("sharding-count", "4")));
    }
    
    private AlgorithmConfiguration createKeyGeneratorConfiguration() {
        return new AlgorithmConfiguration("SNOWFLAKE", new Properties());
    }
}
