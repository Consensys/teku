/*
 * Copyright 2020 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.storage.server.sql;

import static tech.pegasys.teku.storage.server.sql.ParamConverter.convertParams;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

public class AbstractSqlTransaction implements AutoCloseable {

  private final AtomicBoolean closed = new AtomicBoolean(false);

  private final TransactionStatus transaction;
  private final PlatformTransactionManager transactionManager;
  private final JdbcOperations jdbc;

  protected AbstractSqlTransaction(
      final TransactionStatus transaction,
      final PlatformTransactionManager transactionManager,
      final JdbcOperations jdbc) {
    this.transaction = transaction;
    this.transactionManager = transactionManager;
    this.jdbc = jdbc;
  }

  /** Execute the specified SQL and return the number of affected rows. */
  protected int execSql(final String sql, final Object... params) {
    return jdbc.update(sql, convertParams(params));
  }

  public void commit() {
    if (closed.compareAndSet(false, true)) {
      transactionManager.commit(transaction);
    }
  }

  @Override
  public void close() {
    if (closed.compareAndSet(false, true)) {
      transactionManager.rollback(transaction);
    }
  }
}
