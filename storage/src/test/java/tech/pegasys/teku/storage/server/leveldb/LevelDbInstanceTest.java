/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.storage.server.leveldb;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.primitives.Ints;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.storage.server.rocksdb.RocksDbConfiguration;
import tech.pegasys.teku.storage.server.rocksdb.core.ColumnEntry;
import tech.pegasys.teku.storage.server.rocksdb.core.RocksDbAccessor;
import tech.pegasys.teku.storage.server.rocksdb.core.RocksDbAccessor.RocksDbTransaction;
import tech.pegasys.teku.storage.server.rocksdb.schema.RocksDbColumn;
import tech.pegasys.teku.storage.server.rocksdb.schema.RocksDbVariable;
import tech.pegasys.teku.storage.server.rocksdb.serialization.RocksDbSerializer;

class LevelDbInstanceTest {

  private static final IntSerializer LONG_SERIALIZER = new IntSerializer();

  private static final RocksDbVariable<Integer> variable1 =
      RocksDbVariable.create(0, LONG_SERIALIZER);
  private static final RocksDbVariable<Integer> variable2 =
      RocksDbVariable.create(1, LONG_SERIALIZER);

  private static final RocksDbColumn<Integer, Integer> column1 =
      RocksDbColumn.create(1, LONG_SERIALIZER, LONG_SERIALIZER);
  private static final RocksDbColumn<Integer, Integer> column2 =
      RocksDbColumn.create(2, LONG_SERIALIZER, LONG_SERIALIZER);
  private static final RocksDbColumn<Integer, Integer> column3 =
      RocksDbColumn.create(3, LONG_SERIALIZER, LONG_SERIALIZER);

  private RocksDbAccessor instance;

  @BeforeEach
  void setUp(@TempDir final Path tempDir) {
    instance =
        LevelDbInstanceFactory.create(
            new NoOpMetricsSystem(),
            TekuMetricCategory.STORAGE,
            RocksDbConfiguration.v6SingleDefaults().withDatabaseDir(tempDir),
            List.of(column1, column2, column3));
  }

  @AfterEach
  void tearDown() throws Exception {
    instance.close();
  }

  @Test
  void shouldStoreAndLoadSimpleKey() {
    try (final RocksDbTransaction update = instance.startTransaction()) {
      update.put(column1, 0, 0);
      update.put(column1, 1, 1);
      update.put(column2, 1, 2);
      update.put(column3, 0, 3);
      update.commit();
    }

    assertThat(instance.get(column1, 0)).contains(0);
    assertThat(instance.get(column1, 1)).contains(1);
    assertThat(instance.get(column1, 2)).isEmpty();

    assertThat(instance.get(column2, 0)).isEmpty();
    assertThat(instance.get(column2, 1)).contains(2);

    assertThat(instance.get(column3, 0)).contains(3);
    assertThat(instance.get(column3, 1)).isEmpty();
  }

  @Test
  void shouldStoreAndLoadVariables() {
    assertThat(instance.get(variable1)).isEmpty();
    assertThat(instance.get(variable2)).isEmpty();

    try (final RocksDbTransaction update = instance.startTransaction()) {
      update.put(variable1, 0);
      update.put(variable2, 1);
      update.commit();
    }

    assertThat(instance.get(variable1)).contains(0);
    assertThat(instance.get(variable2)).contains(1);
  }

  @Test
  void stream_includeValuesFromMiddleOfColumnRange_startAndEndExist() {
    update(
        tx -> {
          for (int i = 0; i < 10; i++) {
            tx.put(column1, i, i);
          }
        });

    try (final Stream<ColumnEntry<Integer, Integer>> stream = instance.stream(column1, 2, 8)) {
      assertThat(stream)
          .containsExactly(
              ColumnEntry.create(2, 2),
              ColumnEntry.create(3, 3),
              ColumnEntry.create(4, 4),
              ColumnEntry.create(5, 5),
              ColumnEntry.create(6, 6),
              ColumnEntry.create(7, 7),
              ColumnEntry.create(8, 8));
    }
  }

  @Test
  void stream_includeAllValuesInDatabase_startAndEndExist() {
    update(
        tx -> {
          for (int i = 0; i < 10; i++) {
            tx.put(column1, i, i);
          }
        });

    try (final Stream<ColumnEntry<Integer, Integer>> stream = instance.stream(column1, 0, 9)) {
      assertThat(stream)
          .containsExactly(
              ColumnEntry.create(0, 0),
              ColumnEntry.create(1, 1),
              ColumnEntry.create(2, 2),
              ColumnEntry.create(3, 3),
              ColumnEntry.create(4, 4),
              ColumnEntry.create(5, 5),
              ColumnEntry.create(6, 6),
              ColumnEntry.create(7, 7),
              ColumnEntry.create(8, 8),
              ColumnEntry.create(9, 9));
    }
  }

  @Test
  void stream_includeAllValuesInDatabase_startAndEndBeyondRange() {
    update(
        tx -> {
          for (int i = 2; i < 8; i++) {
            tx.put(column1, i, i);
          }
        });

    try (final Stream<ColumnEntry<Integer, Integer>> stream = instance.stream(column1, 0, 10)) {
      assertThat(stream)
          .containsExactly(
              ColumnEntry.create(2, 2),
              ColumnEntry.create(3, 3),
              ColumnEntry.create(4, 4),
              ColumnEntry.create(5, 5),
              ColumnEntry.create(6, 6),
              ColumnEntry.create(7, 7));
    }
  }

  @Test
  void stream_includeAllValuesFromMiddleColumn_startAndEndBeyondRange() {
    update(
        tx -> {
          tx.put(column1, 5, 5);
          tx.put(column1, 6, 6);
          for (int i = 2; i < 8; i++) {
            tx.put(column2, i, i);
          }
          tx.put(column3, 0, 0);
          tx.put(column3, 8, 8);
          tx.put(variable1, 6);
          tx.put(variable2, 7);
        });

    try (final Stream<ColumnEntry<Integer, Integer>> stream = instance.stream(column2, 0, 10)) {
      assertThat(stream)
          .containsExactly(
              ColumnEntry.create(2, 2),
              ColumnEntry.create(3, 3),
              ColumnEntry.create(4, 4),
              ColumnEntry.create(5, 5),
              ColumnEntry.create(6, 6),
              ColumnEntry.create(7, 7));
    }
  }

  @Test
  void getFloorEntry_shouldGetMatchingEntryWhenKeyExists() {
    update(
        tx -> {
          tx.put(column1, 1, 1);
          tx.put(column1, 2, 2);
          tx.put(column1, 3, 3);
          tx.put(column1, 4, 4);
        });

    assertThat(instance.getFloorEntry(column1, 3)).contains(ColumnEntry.create(3, 3));
  }

  @Test
  void getFloorEntry_shouldGetClosestPriorEntryWhenKeyDoesNotExist() {
    update(
        tx -> {
          tx.put(column1, 1, 1);
          tx.put(column1, 4, 4);
        });

    assertThat(instance.getFloorEntry(column1, 3)).contains(ColumnEntry.create(1, 1));
  }

  @Test
  void getFloorEntry_shouldBeEmptyWhenNoPriorKey() {
    update(
        tx -> {
          tx.put(column1, 3, 3);
          tx.put(column1, 4, 4);
        });

    assertThat(instance.getFloorEntry(column1, 2)).isEmpty();
  }

  @Test
  void getFloorEntry_shouldBeEmptyWhenNoPriorKeyInColumn() {
    update(
        tx -> {
          tx.put(column1, 1, 1);
          tx.put(column1, 4, 4);

          tx.put(column2, 3, 3);
          tx.put(column2, 4, 4);
        });

    assertThat(instance.getFloorEntry(column2, 2)).isEmpty();
  }

  @Test
  void getFloorEntry_shouldBeEmptyWhenKeyAfterLastEntryInColumn() {
    update(
        tx -> {
          tx.put(column1, 1, 1);
          tx.put(column1, 4, 4);

          tx.put(column2, 3, 3);
          tx.put(column2, 4, 4);
        });

    assertThat(instance.getFloorEntry(column1, 5)).contains(ColumnEntry.create(4,4));
  }

  @Test
  void getFloorEntry_shouldBeEmptyWhenKeyAfterLastEntryInDatabase() {
    update(
        tx -> {
          tx.put(column1, 1, 1);
          tx.put(column1, 4, 4);
        });

    assertThat(instance.getFloorEntry(column1, 5)).contains(ColumnEntry.create(4,4));
  }

  private void update(final Consumer<RocksDbTransaction> updater) {
    try (final RocksDbTransaction transaction = instance.startTransaction()) {
      updater.accept(transaction);
      transaction.commit();
    }
  }

  private static class IntSerializer implements RocksDbSerializer<Integer> {

    @Override
    public Integer deserialize(final byte[] data) {
      return Ints.fromByteArray(data);
    }

    @Override
    public byte[] serialize(final Integer value) {
      return Ints.toByteArray(value);
    }
  }
}
