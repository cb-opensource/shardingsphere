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

package org.apache.shardingsphere.sql.parser.sql.dialect.statement.sqlserver.dml;

import lombok.Setter;
import org.apache.shardingsphere.sql.parser.sql.common.segment.generic.OutputSegment;
import org.apache.shardingsphere.sql.parser.sql.common.segment.generic.WithSegment;
import org.apache.shardingsphere.sql.parser.sql.dialect.segment.sqlserver.hint.WithTableHintSegment;
import org.apache.shardingsphere.sql.parser.sql.common.statement.dml.InsertStatement;
import org.apache.shardingsphere.sql.parser.sql.dialect.segment.sqlserver.exec.ExecSegment;
import org.apache.shardingsphere.sql.parser.sql.dialect.statement.sqlserver.SQLServerStatement;

import java.util.Optional;

/**
 * SQLServer insert statement.
 */
@Setter
public final class SQLServerInsertStatement extends InsertStatement implements SQLServerStatement {
    
    private WithSegment withSegment;
    
    private OutputSegment outputSegment;
    
    private ExecSegment execSegment;
    
    private WithTableHintSegment withTableHintSegment;
    
    /**
     * Get with segment.
     *
     * @return with segment.
     */
    public Optional<WithSegment> getWithSegment() {
        return Optional.ofNullable(withSegment);
    }
    
    /**
     * Get output segment.
     *
     * @return output segment.
     */
    public Optional<OutputSegment> getOutputSegment() {
        return Optional.ofNullable(outputSegment);
    }
    
    /**
     * Get execute segment.
     *
     * @return execute segment.
     */
    public Optional<ExecSegment> getExecSegment() {
        return Optional.ofNullable(execSegment);
    }
    
    /**
     * Get with table hint segment.
     *
     * @return with table hint segment.
     */
    public Optional<WithTableHintSegment> getWithTableHintSegment() {
        return Optional.ofNullable(withTableHintSegment);
    }
}
