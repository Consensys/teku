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

package org.ethereum.beacon.db;

import com.google.common.annotations.VisibleForTesting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.beacon.crypto.Hashes;
import org.ethereum.beacon.db.flush.BufferSizeObserver;
import org.ethereum.beacon.db.flush.DatabaseFlusher;
import org.ethereum.beacon.db.flush.InstantFlusher;
import org.ethereum.beacon.db.source.CacheSizeEvaluator;
import org.ethereum.beacon.db.source.DataSource;
import org.ethereum.beacon.db.source.StorageEngineSource;
import org.ethereum.beacon.db.source.WriteBuffer;
import org.ethereum.beacon.db.source.impl.MemSizeEvaluators;
import org.ethereum.beacon.db.source.impl.XorDataSource;
import tech.pegasys.artemis.util.bytes.BytesValue;

/**
 * A database that designed to work on top of various key-value storage engines like RocksDB,
 * LevelDB, etc.
 *
 * <p>Main design parts:
 *
 * <ul>
 *   <li>source of {@link StorageEngineSource} type -- represents underlying storage engine
 *   <li>an instance of {@link WriteBuffer} -- memory buffer that accumulates changes made between
 *       flushes
 *   <li>an instance of {@link DatabaseFlusher} -- flushing strategy
 * </ul>
 */
public class EngineDrivenDatabase implements Database {

  private static final Logger logger = LogManager.getLogger(EngineDrivenDatabase.class);

  private final StorageEngineSource<BytesValue> source;
  private final WriteBuffer<BytesValue, BytesValue> writeBuffer;
  private final DatabaseFlusher flusher;

  EngineDrivenDatabase(
      StorageEngineSource<BytesValue> source,
      WriteBuffer<BytesValue, BytesValue> writeBuffer,
      DatabaseFlusher flusher) {
    this.source = source;
    this.writeBuffer = writeBuffer;
    this.flusher = flusher;
  }

  /**
   * Given storage engine and buffer size creates a new instance of {@link EngineDrivenDatabase}.
   *
   * <p><strong>Note:</strong> instantiated flushing strategy depends on buffer limit value; if this
   * value is greater than zero then {@link BufferSizeObserver} is used as a strategy, otherwise,
   * {@link InstantFlusher} is created.
   *
   * @param storageEngineSource an engine-based source.
   * @param bufferLimitInBytes a buffer limit in bytes.
   * @return a new instance.
   */
  public static EngineDrivenDatabase create(
      StorageEngineSource<BytesValue> storageEngineSource, long bufferLimitInBytes) {
    WriteBuffer<BytesValue, BytesValue> buffer =
        new WriteBuffer<>(
            storageEngineSource,
            CacheSizeEvaluator.getInstance(MemSizeEvaluators.BytesValueEvaluator),
            true);
    DatabaseFlusher flusher =
        bufferLimitInBytes > 0
            ? BufferSizeObserver.create(buffer, bufferLimitInBytes)
            : new InstantFlusher(buffer);

    return new EngineDrivenDatabase(storageEngineSource, buffer, flusher);
  }

  /**
   * A shortcut that spawns an instance with {@link InstantFlusher} flushing strategy.
   *
   * @param storageEngineSource an engine-based source.
   * @return a new instance.
   */
  public static EngineDrivenDatabase createWithInstantFlusher(
      StorageEngineSource<BytesValue> storageEngineSource) {
    return create(storageEngineSource, -1);
  }

  @Override
  public DataSource<BytesValue, BytesValue> createStorage(String name) {
    source.open();
    return new XorDataSource<>(writeBuffer, Hashes.sha256(BytesValue.wrap(name.getBytes())));
  }

  @Override
  public void commit() {
    flusher.commit();
  }

  @Override
  public void close() {
    logger.info("Closing underlying database storage...");
    flusher.flush();
    source.close();
  }

  @VisibleForTesting
  WriteBuffer<BytesValue, BytesValue> getWriteBuffer() {
    return writeBuffer;
  }
}
