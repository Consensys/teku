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

package org.ethereum.beacon.discovery.database;

import org.ethereum.beacon.discovery.type.BytesValue;

/**
 * Data source supplier based on a specific key-value storage engine like RocksDB, LevelDB, etc.
 *
 * <p>Underlying implementation MUST support batch updates and MAY be aware of open and close
 * operations.
 *
 * @param <ValueType> a value type.
 */
public interface StorageEngineSource<ValueType>
    extends BatchUpdateDataSource<BytesValue, ValueType> {

  /**
   * Opens key-value storage.
   *
   * <p><strong>Note:</strong> an implementation MUST take care of double open calls.
   */
  void open();

  /** Closes key-value storage. */
  void close();
}
