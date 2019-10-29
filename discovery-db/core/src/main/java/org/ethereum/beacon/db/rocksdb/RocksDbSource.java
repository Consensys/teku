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

package org.ethereum.beacon.db.rocksdb;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.annotation.Nonnull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.beacon.db.source.StorageEngineSource;
import org.ethereum.beacon.db.util.AutoCloseableLock;
import org.rocksdb.BlockBasedTableConfig;
import org.rocksdb.BloomFilter;
import org.rocksdb.CompressionType;
import org.rocksdb.Options;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;
import tech.pegasys.artemis.util.bytes.BytesValue;

/**
 * Data source supplied by <a href="https://github.com/facebook/rocksdb">RocksDB</a> storage engine.
 */
public class RocksDbSource implements StorageEngineSource<BytesValue> {

  private static final Logger logger = LogManager.getLogger(RocksDbSource.class);

  private ReadOptions readOptions;
  private final Path dbPath;

  private final ReadWriteLock dbLock = new ReentrantReadWriteLock();
  private final AutoCloseableLock crudLock = AutoCloseableLock.wrap(dbLock.readLock());
  private final AutoCloseableLock openCloseLock = AutoCloseableLock.wrap(dbLock.writeLock());

  private RocksDB db;
  private boolean opened = false;

  public RocksDbSource(Path dbPath) {
    this.dbPath = dbPath;
  }

  @Override
  public void open() {
    if (opened) {
      return;
    }

    RocksDB.loadLibrary();
    try (AutoCloseableLock l = openCloseLock.lock();
        Options options = new Options()) {
      options.setCreateIfMissing(true);
      options.setCompressionType(CompressionType.LZ4_COMPRESSION);
      options.setBottommostCompressionType(CompressionType.ZSTD_COMPRESSION);
      options.setLevelCompactionDynamicLevelBytes(true);
      options.setMaxOpenFiles(512);
      options.setIncreaseParallelism(Math.min(1, Runtime.getRuntime().availableProcessors() / 2));

      final BlockBasedTableConfig tableCfg = new BlockBasedTableConfig();
      tableCfg.setBlockSize(16 * 1024);
      tableCfg.setBlockCacheSize(32 * 1024 * 1024);
      tableCfg.setCacheIndexAndFilterBlocks(true);
      tableCfg.setPinL0FilterAndIndexBlocksInCache(true);
      tableCfg.setFilter(new BloomFilter(10, false));
      options.setTableFormatConfig(tableCfg);

      readOptions = new ReadOptions();
      readOptions = readOptions.setPrefixSameAsStart(true);

      db = RocksDB.open(options, dbPath.toString());
      opened = true;
    } catch (RocksDBException e) {
      logger.error("Failed to open database {}: {}", dbPath.toString(), e.getMessage());
      throw new RuntimeException(e);
    }
  }

  @Override
  public void close() {
    try (AutoCloseableLock l = openCloseLock.lock()) {
      db.close();
      opened = false;
    }
  }

  @Override
  public void batchUpdate(Map<BytesValue, BytesValue> updates) {
    assert opened;
    try (AutoCloseableLock l = crudLock.lock();
        WriteBatch batch = new WriteBatch();
        WriteOptions writeOptions = new WriteOptions()) {
      for (Map.Entry<BytesValue, BytesValue> entry : updates.entrySet()) {
        if (entry.getValue() == null) {
          batch.remove(entry.getKey().getArrayUnsafe());
        } else {
          batch.put(entry.getKey().getArrayUnsafe(), entry.getValue().getArrayUnsafe());
        }
      }
      db.write(writeOptions, batch);
    } catch (RocksDBException e) {
      logger.error("Failed to do batchUpdate: {}", e.getMessage());
      throw new RuntimeException(e);
    }
  }

  @Override
  public Optional<BytesValue> get(@Nonnull BytesValue key) {
    assert opened;
    Objects.requireNonNull(key);

    try (AutoCloseableLock l = crudLock.lock()) {
      return Optional.ofNullable(db.get(readOptions, key.getArrayUnsafe()))
          .flatMap(bytes -> Optional.of(BytesValue.wrap(bytes)));
    } catch (RocksDBException e) {
      logger.error("Failed to get({}): {}", key, e.getMessage());
      throw new RuntimeException(e);
    }
  }

  @Override
  public void put(@Nonnull BytesValue key, @Nonnull BytesValue value) {
    assert opened;
    Objects.requireNonNull(key);
    Objects.requireNonNull(value);

    try (AutoCloseableLock l = crudLock.lock()) {
      db.put(key.getArrayUnsafe(), value.getArrayUnsafe());
    } catch (RocksDBException e) {
      logger.error("Failed to put({}, {}): {}", key, value, e.getMessage());
      throw new RuntimeException(e);
    }
  }

  @Override
  public void remove(@Nonnull BytesValue key) {
    assert opened;
    Objects.requireNonNull(key);

    try (AutoCloseableLock l = crudLock.lock()) {
      db.delete(key.getArrayUnsafe());
    } catch (RocksDBException e) {
      logger.error("Failed to remove({}): {}", key, e.getMessage());
      throw new RuntimeException(e);
    }
  }

  @Override
  public void flush() {
    // flushes are managed by RocksDB
  }
}
