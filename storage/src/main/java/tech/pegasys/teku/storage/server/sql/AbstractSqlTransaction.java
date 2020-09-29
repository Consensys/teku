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

import static java.util.stream.Collectors.toList;
import static tech.pegasys.teku.storage.server.sql.ParamConverter.convertParams;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

public class AbstractSqlTransaction extends SqlQueryUtils implements AutoCloseable {

  private final AtomicBoolean closed = new AtomicBoolean(false);

  private final TransactionStatus transaction;
  private final PlatformTransactionManager transactionManager;

  protected AbstractSqlTransaction(
      final TransactionStatus transaction,
      final PlatformTransactionManager transactionManager,
      final JdbcOperations jdbc) {
    super(jdbc);
    this.transaction = transaction;
    this.transactionManager = transactionManager;
  }

  /** Execute the specified SQL and return the number of affected rows. */
  protected int execSql(final String sql, final Object... params) {
    return jdbc.update(sql, convertParams(params));
  }

  protected void batchUpdate(final String sql, final Collection<?> params) {
    jdbc.batchUpdate(sql, params.stream().map(ParamConverter::convertParams).collect(toList()));
  }

  protected void batchUpdate(final String sql, final Stream<Object[]> params) {
    jdbc.batchUpdate(sql, params.map(ParamConverter::convertParams).collect(toList()));
  }

  public void commit() {
    if (closed.compareAndSet(false, true)) {
      doCommit();
    }
  }

  protected void doCommit() {
    transactionManager.commit(transaction);
  }

  @Override
  public void close() {
    if (closed.compareAndSet(false, true)) {
      doRollback();
    }
  }

  protected void doRollback() {
    transactionManager.rollback(transaction);
  }
}
