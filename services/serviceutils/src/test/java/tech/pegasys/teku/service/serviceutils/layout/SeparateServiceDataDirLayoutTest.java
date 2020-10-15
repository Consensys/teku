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

package tech.pegasys.teku.service.serviceutils.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static tech.pegasys.teku.service.serviceutils.layout.SeparateServiceDataDirLayout.BEACON_DATA_DIR_NAME;
import static tech.pegasys.teku.service.serviceutils.layout.SeparateServiceDataDirLayout.VALIDATOR_DATA_DIR_NAME;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SeparateServiceDataDirLayoutTest {
  @TempDir Path tempDir;

  private SeparateServiceDataDirLayout layout;

  @BeforeEach
  void setUp() {
    layout = new SeparateServiceDataDirLayout(tempDir, Optional.empty(), Optional.empty());
  }

  @Test
  void shouldUseDefaultBeaconDataDirectory() {
    assertThat(layout.getBeaconDataDirectory()).isEqualTo(tempDir.resolve(BEACON_DATA_DIR_NAME));
  }

  @Test
  void shouldUseDefaultValidatorDataDirectory() {
    assertThat(layout.getValidatorDataDirectory())
        .isEqualTo(tempDir.resolve(VALIDATOR_DATA_DIR_NAME));
  }

  @Test
  void shouldMigrateFromOldLayoutWithCustomDataPath() throws IOException {
    createOldDataDirLayout(tempDir);

    layout.migrateIfNecessary();

    final Path beaconDataDirectory = layout.getBeaconDataDirectory();
    assertThat(beaconDataDirectory).isEqualTo(tempDir.resolve(BEACON_DATA_DIR_NAME));
    assertBeaconDataDirectoryContent(beaconDataDirectory);

    final Path validatorDataDirectory = layout.getValidatorDataDirectory();
    assertThat(validatorDataDirectory).isEqualTo(tempDir.resolve(VALIDATOR_DATA_DIR_NAME));
    assertValidatorDataDirectoryContent(validatorDataDirectory);
  }

  @Test
  void shouldMigrateFromOldLayoutWithDefaultDataPath() throws IOException {
    // Should move content out of the data subdirectory to adapt to changed default path
    createOldTekuDirLayout(tempDir);

    layout.migrateIfNecessary();

    final Path beaconDataDirectory = layout.getBeaconDataDirectory();
    assertThat(beaconDataDirectory).isEqualTo(tempDir.resolve(BEACON_DATA_DIR_NAME));
    assertBeaconDataDirectoryContent(beaconDataDirectory);

    final Path validatorDataDirectory = layout.getValidatorDataDirectory();
    assertThat(validatorDataDirectory).isEqualTo(tempDir.resolve(VALIDATOR_DATA_DIR_NAME));
    assertValidatorDataDirectoryContent(validatorDataDirectory);

    assertLogDirectoryContent(tempDir.resolve("logs"));
  }

  @Test
  void shouldNotMakeChangesIfDataDirIsEmpty() throws IOException {
    layout.migrateIfNecessary();

    assertThat(tempDir).isEmptyDirectory();
  }

  /*
  Manually create the old layout so we know it has the same layout even if the prod code changes

  ~/Library/teku
  ├── data
  │   ├── archive
  │   │   ├── <rocksdb stuff>
  │   ├── db
  │   │   ├── <rocksdb stuff>
  │   ├── db.version
  │   ├── kvstore
  │   │   ├── generated-node-key.dat
  │   │   └── local-enr-seqno.dat
  │   ├── metadata.yml
  │   ├── network.yml
  │   └── validators
  │       └── slashprotection
  │           └── b89bebc699769726a318c8e9971bd3171297c61aea4a6578a7a4f94b547dcba5bac16a89108b6b6a1fe3695d1a874a0b.yml
  └── logs
      └── teku.log
   */
  private void createOldTekuDirLayout(final Path tekuDir) throws IOException {
    final Path dataDir = tekuDir.resolve("data");
    createOldDataDirLayout(dataDir);
    newFile(tekuDir.resolve(Path.of("logs", "teku.log")), "log content");
  }

  private void createOldDataDirLayout(final Path dataDir) throws IOException {
    newFile(dataDir.resolve(Path.of("db", "rocksdb")), "db-content");
    newFile(dataDir.resolve(Path.of("archive", "rocksdb")), "archive-content");
    newFile(dataDir.resolve(Path.of("kvstore", "kvvalue.yml")), "kvstore-content");
    newFile(dataDir.resolve("db.version"), "1");
    newFile(dataDir.resolve("metadata.yml"), "metadata: true");
    newFile(dataDir.resolve("network.yml"), "network: true");
    newFile(
        dataDir.resolve(
            Path.of(
                "validators",
                "slashprotection",
                "b89bebc699769726a318c8e9971bd3171297c61aea4a6578a7a4f94b547dcba5bac16a89108b6b6a1fe3695d1a874a0b.yml")),
        "slashing-record");
  }

  private void assertBeaconDataDirectoryContent(final Path beaconDataDirectory) {
    assertThat(beaconDataDirectory).isDirectory();
    assertThat(beaconDataDirectory.resolve("db")).isDirectory();
    assertThat(beaconDataDirectory.resolve("archive")).isDirectory();
    assertThat(beaconDataDirectory.resolve("kvstore")).isDirectory();
    assertThat(beaconDataDirectory.resolve(Path.of("db", "rocksdb"))).hasContent("db-content");
    assertThat(beaconDataDirectory.resolve(Path.of("archive", "rocksdb")))
        .hasContent("archive-content");
    assertThat(beaconDataDirectory.resolve(Path.of("kvstore", "kvvalue.yml")))
        .hasContent("kvstore-content");
    assertThat(beaconDataDirectory.resolve("db.version")).hasContent("1");
    assertThat(beaconDataDirectory.resolve("metadata.yml")).hasContent("metadata: true");
    assertThat(beaconDataDirectory.resolve("network.yml")).hasContent("network: true");
  }

  private void assertValidatorDataDirectoryContent(final Path validatorDataDirectory) {
    assertThat(
            validatorDataDirectory.resolve(
                Path.of(
                    "slashprotection",
                    "b89bebc699769726a318c8e9971bd3171297c61aea4a6578a7a4f94b547dcba5bac16a89108b6b6a1fe3695d1a874a0b.yml")))
        .hasContent("slashing-record");
  }

  private void assertLogDirectoryContent(final Path logDir) {
    assertThat(logDir.resolve("teku.log")).hasContent("log content");
  }

  private void mkdirs(final Path path) {
    final File file = path.toFile();
    if (!file.mkdirs() && !file.isDirectory()) {
      fail("Unable to create directory " + path.toAbsolutePath());
    }
  }

  private void newFile(final Path path, final String content) throws IOException {
    mkdirs(path.getParent());
    Files.writeString(path, content, StandardOpenOption.CREATE_NEW);
  }
}
