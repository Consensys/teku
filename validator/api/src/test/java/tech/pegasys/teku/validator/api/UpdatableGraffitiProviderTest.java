/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.validator.api;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.validator.api.GraffitiManager.GRAFFITI_DIR;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.techu.service.serviceutils.layout.SimpleDataDirLayout;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class UpdatableGraffitiProviderTest {
  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());

  private final BLSPublicKey publicKey = dataStructureUtil.randomPublicKey();
  private final Bytes32 defaultGraffiti = dataStructureUtil.randomBytes32();
  private final GraffitiProvider defaultProvider = () -> Optional.of(defaultGraffiti);

  private DataDirLayout dataDirLayout;
  private UpdatableGraffitiProvider provider;

  @Test
  void get_shouldGetDefaultWhenStorageEmpty(@TempDir final Path tempDir) {
    dataDirLayout = new SimpleDataDirLayout(tempDir);
    provider = new UpdatableGraffitiProvider(dataDirLayout, publicKey, defaultProvider);

    assertThat(provider.get()).hasValue(defaultGraffiti);
  }

  @Test
  void get_shouldGetEmptyWhenStorageAndDefaultEmpty(@TempDir final Path tempDir) {
    dataDirLayout = new SimpleDataDirLayout(tempDir);
    provider = new UpdatableGraffitiProvider(dataDirLayout, publicKey, Optional::empty);

    assertThat(provider.get()).isEmpty();
  }

  @Test
  void get_shouldGetStorageGraffitiWhenAvailable(@TempDir final Path tempDir) throws IOException {
    dataDirLayout = new SimpleDataDirLayout(tempDir);
    provider = new UpdatableGraffitiProvider(dataDirLayout, publicKey, defaultProvider);
    final Bytes32 storedGraffiti = Bytes32Parser.toBytes32("Test graffiti");
    storeGraffiti(publicKey, storedGraffiti);

    assertThat(provider.get()).hasValue(storedGraffiti);
  }

  @Test
  void get_shouldUseDefaultProviderWhenStoredGraffitiTooLong(@TempDir final Path tempDir)
      throws IOException {
    dataDirLayout = new SimpleDataDirLayout(tempDir);
    provider = new UpdatableGraffitiProvider(dataDirLayout, publicKey, defaultProvider);
    final Bytes storedGraffiti =
        Bytes.of("This graffiti is a bit too long!!".getBytes(StandardCharsets.UTF_8));
    storeGraffiti(publicKey, storedGraffiti);

    assertThat(provider.get()).hasValue(defaultGraffiti);
  }

  @Test
  @DisabledOnOs(OS.WINDOWS) // Can't set permissions on Windows
  void get_shouldUseDefaultWhenFileNotReadable(@TempDir final Path tempDir) throws IOException {
    dataDirLayout = new SimpleDataDirLayout(tempDir);
    provider = new UpdatableGraffitiProvider(dataDirLayout, publicKey, defaultProvider);
    final Path path =
        dataDirLayout
            .getValidatorDataDirectory()
            .resolve(GRAFFITI_DIR)
            .resolve(GraffitiManager.resolveFileName(publicKey));
    final Bytes32 storedGraffiti = Bytes32Parser.toBytes32("Test graffiti");
    assertThat(path.getParent().toFile().mkdirs()).isTrue();
    Files.write(path, storedGraffiti.toArray());
    assertThat(path.toFile().setReadable(false)).isTrue();

    assertThat(provider.get()).hasValue(defaultGraffiti);
  }

  @Test
  void get_shouldReturnDefaultWhenEmptyFileAvailable(@TempDir final Path tempDir)
      throws IOException {
    dataDirLayout = new SimpleDataDirLayout(tempDir);
    provider = new UpdatableGraffitiProvider(dataDirLayout, publicKey, defaultProvider);
    storeGraffiti(publicKey, Bytes32.EMPTY);

    assertThat(provider.get()).hasValue(defaultGraffiti);
  }

  private void storeGraffiti(final BLSPublicKey publicKey, final Bytes graffiti)
      throws IOException {
    final Path path =
        dataDirLayout
            .getValidatorDataDirectory()
            .resolve(GRAFFITI_DIR)
            .resolve(GraffitiManager.resolveFileName(publicKey));
    assertThat(path.getParent().toFile().mkdirs()).isTrue();
    Files.write(path, graffiti.toArray());
  }
}
