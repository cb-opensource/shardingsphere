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

package org.apache.shardingsphere.proxy.backend.handler.distsql.ral.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.shardingsphere.error.code.SQLErrorCode;

/**
 * Dist SQL error code.
 */
@RequiredArgsConstructor
@Getter
public enum DistSQLErrorCode implements SQLErrorCode {
    
    UNSUPPORTED_VARIABLE(11001, "11001", "Could not support variable `%s`."),
    
    INVALID_VALUE(11002, "11002", "Invalid value `%s`.");
    
    private final int errorCode;
    
    private final String sqlState;
    
    private final String errorMessage;
    
    /**
     * Value of dist SQL error code.
     * 
     * @param distSQLException dist SQL exception
     * @return dist SQL error code
     */
    public static DistSQLErrorCode valueOf(final DistSQLException distSQLException) {
        if (distSQLException instanceof UnsupportedVariableException) {
            return UNSUPPORTED_VARIABLE;
        }
        if (distSQLException instanceof InvalidValueException) {
            return INVALID_VALUE;
        }
        throw new UnsupportedOperationException("Can not find DistSQL error code from exception: %s", distSQLException);
    }
}
