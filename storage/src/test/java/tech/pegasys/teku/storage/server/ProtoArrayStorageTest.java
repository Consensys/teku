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

package tech.pegasys.teku.storage.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.protoarray.ProtoArray;
import tech.pegasys.teku.protoarray.ProtoArraySnapshot;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.storage.storageSystem.StorageSystemArgumentsProvider;

public class ProtoArrayStorageTest {

  @TempDir Path dataDirectory;
  private StorageSystem storageSystem;
  private ProtoArrayStorage protoArrayStorage;

  private void setup(
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier) {
    storageSystem = storageSystemSupplier.get(dataDirectory);
    storageSystem.chainUpdater().initializeGenesis();
    protoArrayStorage = storageSystem.createProtoArrayStorage();
  }

  @AfterEach
  void cleanUp() throws Exception {
    storageSystem.close();
  }

  @ParameterizedTest(name = "{0}")
  @ArgumentsSource(StorageSystemArgumentsProvider.class)
  public void shouldReturnEmptyIfThereIsNoProtoArrayOnDisk(
      final String storageType,
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier)
      throws Exception {
    setup(storageSystemSupplier);
    SafeFuture<Optional<ProtoArraySnapshot>> future = protoArrayStorage.getProtoArraySnapshot();
    assertThat(future.isDone()).isTrue();
    assertThat(future.get().isPresent()).isFalse();
  }

  @ParameterizedTest(name = "{0}")
  @ArgumentsSource(StorageSystemArgumentsProvider.class)
  public void shouldReturnSameSetOfNodes(
      final String storageType,
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier)
      throws Exception {
    setup(storageSystemSupplier);

    // init ProtoArray
    ProtoArray protoArray =
        new ProtoArray(
            10000,
            UInt64.valueOf(100),
            UInt64.valueOf(99),
            UInt64.ZERO,
            new ArrayList<>(),
            new HashMap<>());

    // add block 1
    protoArray.onBlock(
        UInt64.valueOf(10000),
        Bytes32.fromHexString("0xdeadbeef"),
        Bytes32.ZERO,
        Bytes32.ZERO,
        UInt64.valueOf(101),
        UInt64.valueOf(100));

    // add block 2
    protoArray.onBlock(
        UInt64.valueOf(10001),
        Bytes32.fromHexString("0x1234"),
        Bytes32.fromHexString("0xdeadbeef"),
        Bytes32.ZERO,
        UInt64.valueOf(101),
        UInt64.valueOf(100));

    ProtoArraySnapshot protoArraySnapshot = ProtoArraySnapshot.create(protoArray);
    protoArrayStorage.onProtoArrayUpdate(protoArraySnapshot);

    SafeFuture<Optional<ProtoArraySnapshot>> future = protoArrayStorage.getProtoArraySnapshot();
    assertThat(future.isDone()).isTrue();
    assertThat(future.get().isPresent()).isTrue();

    ProtoArraySnapshot protoArraySnapshotFromDisk =
        protoArrayStorage.getProtoArraySnapshot().get().get();
    assertThat(protoArraySnapshot).isEqualToComparingFieldByField(protoArraySnapshotFromDisk);
  }

  @ParameterizedTest(name = "{0}")
  @ArgumentsSource(StorageSystemArgumentsProvider.class)
  public void shouldOverwriteTheProtoArray(
      final String storageType,
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier)
      throws Exception {
    setup(storageSystemSupplier);

    // init ProtoArray
    ProtoArray protoArray1 =
        new ProtoArray(
            10000,
            UInt64.valueOf(100),
            UInt64.valueOf(99),
            UInt64.ZERO,
            new ArrayList<>(),
            new HashMap<>());

    ProtoArraySnapshot protoArraySnapshot1 = ProtoArraySnapshot.create(protoArray1);
    protoArrayStorage.onProtoArrayUpdate(protoArraySnapshot1);

    ProtoArray protoArray2 =
        new ProtoArray(
            10000,
            UInt64.valueOf(98),
            UInt64.valueOf(97),
            UInt64.ZERO,
            new ArrayList<>(),
            new HashMap<>());

    // add block 1
    protoArray2.onBlock(
        UInt64.valueOf(10000),
        Bytes32.fromHexString("0xdeadbeef"),
        Bytes32.ZERO,
        Bytes32.ZERO,
        UInt64.valueOf(101),
        UInt64.valueOf(100));

    // add block 2
    protoArray2.onBlock(
        UInt64.valueOf(10001),
        Bytes32.fromHexString("0x1234"),
        Bytes32.fromHexString("0xdeadbeef"),
        Bytes32.ZERO,
        UInt64.valueOf(101),
        UInt64.valueOf(100));

    ProtoArraySnapshot protoArraySnapshot2 = ProtoArraySnapshot.create(protoArray2);
    protoArrayStorage.onProtoArrayUpdate(protoArraySnapshot2);

    ProtoArraySnapshot protoArraySnapshotFromDisk =
        protoArrayStorage.getProtoArraySnapshot().get().get();
    assertThat(protoArraySnapshotFromDisk).isEqualToComparingFieldByField(protoArraySnapshot2);
  }
}
