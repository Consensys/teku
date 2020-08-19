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

package tech.pegasys.teku.storage.storageSystem;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import tech.pegasys.teku.storage.server.DatabaseVersion;
import tech.pegasys.teku.storage.server.VersionedDatabaseFactory;
import tech.pegasys.teku.util.config.StateStorageMode;

public class StorageSystemArgumentsProvider implements ArgumentsProvider {
  private final List<Long> stateStorageFrequencyOptions =
      List.of(VersionedDatabaseFactory.DEFAULT_STORAGE_FREQUENCY);

  @Override
  public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
    final Map<String, StorageSystemSupplier> storageSystems = new HashMap<>();
    for (StateStorageMode mode : StateStorageMode.values()) {
      storageSystems.put(
          "v3 (in-memory)",
          (dataPath) ->
              InMemoryStorageSystemBuilder.create()
                  .version(DatabaseVersion.V3)
                  .storageMode(mode)
                  .build());
      storageSystems.put(
          "v3 (file-backed)",
          (dataPath) ->
              FileBackedStorageSystemBuilder.create()
                  .version(DatabaseVersion.V3)
                  .dataDir(dataPath)
                  .storageMode(mode)
                  .build());
      for (Long storageFrequency : stateStorageFrequencyOptions) {
        storageSystems.put(
            describeStorage("v4 (in-memory)", storageFrequency),
            (dataPath) ->
                InMemoryStorageSystemBuilder.create()
                    .version(DatabaseVersion.V4)
                    .storageMode(mode)
                    .stateStorageFrequency(storageFrequency)
                    .build());
        storageSystems.put(
            describeStorage("v5 (in-memory)", storageFrequency),
            (dataPath) ->
                InMemoryStorageSystemBuilder.create()
                    .version(DatabaseVersion.V5)
                    .storageMode(mode)
                    .stateStorageFrequency(storageFrequency)
                    .build());
        storageSystems.put(
            describeStorage("v4 (file-backed)", storageFrequency),
            (dataPath) ->
                FileBackedStorageSystemBuilder.create()
                    .version(DatabaseVersion.V4)
                    .dataDir(dataPath)
                    .storageMode(mode)
                    .stateStorageFrequency(storageFrequency)
                    .build());
        storageSystems.put(
            describeStorage("v5 (file-backed)", storageFrequency),
            (dataPath) ->
                FileBackedStorageSystemBuilder.create()
                    .version(DatabaseVersion.V5)
                    .dataDir(dataPath)
                    .storageMode(mode)
                    .stateStorageFrequency(storageFrequency)
                    .build());
      }
    }
    return storageSystems.entrySet().stream()
        .map((entry) -> Arguments.of("storage type: " + entry.getKey(), entry.getValue()));
  }

  private String describeStorage(final String baseName, final long storageFrequency) {
    return String.format("%s (storage freq %s)", baseName, storageFrequency);
  }

  @FunctionalInterface
  public interface StorageSystemSupplier {
    StorageSystem get(final Path dataDirectory);
  }
}
