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

package tech.pegasys.teku.storage.server.rocksdb.core;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.rocksdb.BlockBasedTableConfig;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.DBOptions;
import org.rocksdb.Env;
import org.rocksdb.LRUCache;
import org.rocksdb.RocksDBException;
import org.rocksdb.TransactionDB;
import org.rocksdb.TransactionDBOptions;
import tech.pegasys.teku.storage.server.DatabaseStorageException;
import tech.pegasys.teku.storage.server.rocksdb.RocksDbConfiguration;
import tech.pegasys.teku.storage.server.rocksdb.schema.RocksDbColumn;
import tech.pegasys.teku.storage.server.rocksdb.schema.Schema;

public class RocksDbInstanceFactory {
  static {
    RocksDbUtil.loadNativeLibrary();
  }

  public static RocksDbAccessor create(
      final RocksDbConfiguration configuration, final Class<? extends Schema> schema)
      throws DatabaseStorageException {
    // Track resources that need to be closed
    final List<AutoCloseable> resources = new ArrayList<>();

    // Create options
    final TransactionDBOptions txOptions = new TransactionDBOptions();
    final DBOptions dbOptions = createDBOptions(configuration);
    final ColumnFamilyOptions columnFamilyOptions = createColumnFamilyOptions(configuration);
    resources.addAll(List.of(txOptions, dbOptions, columnFamilyOptions));

    List<ColumnFamilyDescriptor> columnDescriptors =
        createColumnFamilyDescriptors(schema, columnFamilyOptions);
    Map<Bytes, RocksDbColumn<?, ?>> columnsById =
        Schema.streamColumns(schema)
            .collect(Collectors.toMap(RocksDbColumn::getId, Function.identity()));

    try {
      // columnHandles will be filled when the db is opened
      final List<ColumnFamilyHandle> columnHandles = new ArrayList<>(columnDescriptors.size());
      final TransactionDB db =
          TransactionDB.open(
              dbOptions,
              txOptions,
              configuration.getDatabaseDir().toString(),
              columnDescriptors,
              columnHandles);

      final ImmutableMap.Builder<RocksDbColumn<?, ?>, ColumnFamilyHandle> builder =
          ImmutableMap.builder();
      for (ColumnFamilyHandle columnHandle : columnHandles) {
        final Bytes columnId = Bytes.wrap(columnHandle.getName());
        final RocksDbColumn<?, ?> rocksDbColumn = columnsById.get(columnId);
        if (rocksDbColumn != null) {
          // We need to check for null because the default column will not match a RocksDbColumn
          builder.put(rocksDbColumn, columnHandle);
        }
        resources.add(columnHandle);
      }
      final ImmutableMap<RocksDbColumn<?, ?>, ColumnFamilyHandle> columnHandlesMap =
          builder.build();
      final ColumnFamilyHandle defaultHandle = getDefaultHandle(columnHandles);
      resources.add(db);

      return new RocksDbInstance(db, defaultHandle, columnHandlesMap, resources);
    } catch (RocksDBException e) {
      throw new DatabaseStorageException(
          "Failed to open database at path: " + configuration.getDatabaseDir(), e);
    }
  }

  private static ColumnFamilyHandle getDefaultHandle(List<ColumnFamilyHandle> columnHandles) {
    return columnHandles.stream()
        .filter(
            handle -> {
              try {
                return Bytes.wrap(handle.getName()).equals(Schema.DEFAULT_COLUMN_ID);
              } catch (RocksDBException e) {
                throw new DatabaseStorageException("Unable to retrieve default column handle", e);
              }
            })
        .findFirst()
        .orElseThrow(() -> new DatabaseStorageException("No default column defined"));
  }

  private static DBOptions createDBOptions(final RocksDbConfiguration configuration) {
    return new DBOptions()
        .setCreateIfMissing(true)
        .setBytesPerSync(1048576L)
        .setWalBytesPerSync(1048576L)
        .setMaxBackgroundFlushes(2)
        .setMaxOpenFiles(configuration.getMaxOpenFiles())
        .setMaxBackgroundCompactions(configuration.getMaxBackgroundCompactions())
        .setCreateMissingColumnFamilies(true)
        .setEnv(Env.getDefault().setBackgroundThreads(configuration.getBackgroundThreadCount()));
  }

  private static ColumnFamilyOptions createColumnFamilyOptions(
      final RocksDbConfiguration configuration) {
    return new ColumnFamilyOptions()
        .setTableFormatConfig(createBlockBasedTableConfig(configuration));
  }

  private static List<ColumnFamilyDescriptor> createColumnFamilyDescriptors(
      final Class<? extends Schema> schema, final ColumnFamilyOptions columnFamilyOptions) {
    List<ColumnFamilyDescriptor> columnDescriptors =
        Schema.streamColumns(schema)
            .map(
                col -> new ColumnFamilyDescriptor(col.getId().toArrayUnsafe(), columnFamilyOptions))
            .collect(Collectors.toList());
    columnDescriptors.add(
        new ColumnFamilyDescriptor(Schema.DEFAULT_COLUMN_ID.toArrayUnsafe(), columnFamilyOptions));
    return columnDescriptors;
  }

  private static BlockBasedTableConfig createBlockBasedTableConfig(
      final RocksDbConfiguration config) {
    final LRUCache cache = new LRUCache(config.getCacheCapacity());
    return new BlockBasedTableConfig().setBlockCache(cache);
  }
}
