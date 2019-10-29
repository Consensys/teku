/*
 * Copyright 2019 ConsenSys AG.
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

package org.ethereum.beacon.db.source;

/** {@link DataSource} supporting transactions. */
public interface TransactionalDataSource<
        TxOptions extends TransactionalDataSource.TransactionOptions, KeyType, ValueType>
    extends ReadonlyDataSource<KeyType, ValueType> {

  enum IsolationLevel {
    Snapshot,
    RepeatableReads,
    ReadCommitted,
    ReadUncommitted
  }

  interface TransactionOptions {

    IsolationLevel getIsolationLevel();
  }

  interface Transaction<KeyType, ValueType> extends DataSource<KeyType, ValueType> {

    /**
     * Atomically commits all the changes to the underlying storage.
     *
     * @throws TransactionException if conflicting changes were detected
     */
    default void commit() throws TransactionException {
      flush();
    }

    /** Drops all the changes */
    void rollback();
  }

  /**
   * Starts a transaction with specified options
   *
   * @param transactionOptions Transaction options
   * @return An isolated {@link DataSource}.
   * @throws IllegalArgumentException if transaction options supplied are not supported by
   *     implementation
   */
  Transaction<KeyType, ValueType> startTransaction(TxOptions transactionOptions);

  class TransactionException extends RuntimeException {
    public TransactionException(final String message) {
      super(message);
    }

    public TransactionException(final String message, final Throwable cause) {
      super(message, cause);
    }
  }
}
