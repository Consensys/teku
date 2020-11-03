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

import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
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
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier) {
    setup(storageSystemSupplier);
    SafeFuture<Optional<ProtoArraySnapshot>> future = protoArrayStorage.getProtoArraySnapshot();
    assertThatSafeFuture(future).isCompletedWithEmptyOptional();
  }
}
