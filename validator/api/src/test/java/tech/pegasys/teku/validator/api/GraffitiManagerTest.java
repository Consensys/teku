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
import static org.assertj.core.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
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

class GraffitiManagerTest {
  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());
  private final BLSPublicKey publicKey = dataStructureUtil.randomPublicKey();
  private final String graffiti = "Test graffiti";
  private GraffitiManager manager;
  private DataDirLayout dataDirLayout;

  @Test
  @DisabledOnOs(OS.WINDOWS) // Can't set permissions on Windows
  void setGraffiti_shouldThrowExceptionWhenNoDirectory(@TempDir final Path tempDir) {
    assertThat(tempDir.toFile().setWritable(false)).isTrue();
    dataDirLayout = new SimpleDataDirLayout(tempDir);
    manager = new GraffitiManager(dataDirLayout);

    assertThat(getGraffitiManagementDir().toFile().exists()).isFalse();
    assertThat(manager.setGraffiti(dataStructureUtil.randomPublicKey(), graffiti))
        .hasValue("graffiti-management directory does not exist to handle update.");
  }

  @Test
  void setGraffiti_shouldSetGraffitiWhenFileNotExist(@TempDir final Path tempDir) {
    dataDirLayout = new SimpleDataDirLayout(tempDir);
    manager = new GraffitiManager(dataDirLayout);
    assertThat(getGraffitiManagementDir().toFile().exists()).isTrue();

    assertThat(manager.setGraffiti(publicKey, graffiti)).isEmpty();
    checkGraffitiFile(publicKey, graffiti);
  }

  @Test
  void setGraffiti_shouldSetGraffitiWhenFileExist(@TempDir final Path tempDir) throws IOException {
    dataDirLayout = new SimpleDataDirLayout(tempDir);
    manager = new GraffitiManager(dataDirLayout);

    assertThat(getGraffitiManagementDir().resolve(getFileName(publicKey)).toFile().createNewFile())
        .isTrue();

    assertThat(manager.setGraffiti(publicKey, graffiti)).isEmpty();
    checkGraffitiFile(publicKey, graffiti);
  }

  @Test
  @DisabledOnOs(OS.WINDOWS) // Can't set permissions on Windows
  void setGraffiti_shouldThrowExceptionWhenUnableToWriteFile(@TempDir final Path tempDir)
      throws IOException {
    dataDirLayout = new SimpleDataDirLayout(tempDir);
    manager = new GraffitiManager(dataDirLayout);

    final File file = getGraffitiManagementDir().resolve(getFileName(publicKey)).toFile();
    assertThat(file.createNewFile()).isTrue();
    assertThat(file.setWritable(false)).isTrue();

    assertThat(manager.setGraffiti(publicKey, graffiti))
        .hasValue("java.nio.file.AccessDeniedException: " + file);
  }

  @Test
  void setGraffiti_shouldThrowExceptionWhenGraffitiTooBig(@TempDir final Path tempDir) {
    final String invalidGraffiti = "This graffiti is a bit too long!!";
    dataDirLayout = new SimpleDataDirLayout(tempDir);
    manager = new GraffitiManager(dataDirLayout);
    assertThat(getGraffitiManagementDir().toFile().exists()).isTrue();

    assertThat(manager.setGraffiti(publicKey, invalidGraffiti))
        .hasValue(
            "java.lang.IllegalArgumentException: "
                + "'This graffiti is a bit too long!!' converts to 33 bytes. Input must be 32 bytes or less.");
  }

  @Test
  @DisabledOnOs(OS.WINDOWS) // Can't set permissions on Windows
  void deleteGraffiti_shouldThrowExceptionWhenNoDirectory(@TempDir final Path tempDir) {
    assertThat(tempDir.toFile().setWritable(false)).isTrue();
    dataDirLayout = new SimpleDataDirLayout(tempDir);
    manager = new GraffitiManager(dataDirLayout);

    assertThat(getGraffitiManagementDir().toFile().exists()).isFalse();
    assertThat(manager.deleteGraffiti(dataStructureUtil.randomPublicKey()))
        .hasValue("graffiti-management directory does not exist to handle update.");
  }

  @Test
  void deleteGraffiti_shouldSetGraffitiWhenFileNotExist(@TempDir final Path tempDir) {
    dataDirLayout = new SimpleDataDirLayout(tempDir);
    manager = new GraffitiManager(dataDirLayout);
    assertThat(getGraffitiManagementDir().toFile().exists()).isTrue();

    assertThat(manager.deleteGraffiti(publicKey)).isEmpty();
    checkGraffitiFile(publicKey, "");
  }

  @Test
  void deleteGraffiti_shouldSetGraffitiWhenFileExist(@TempDir final Path tempDir)
      throws IOException {
    dataDirLayout = new SimpleDataDirLayout(tempDir);
    manager = new GraffitiManager(dataDirLayout);

    assertThat(getGraffitiManagementDir().resolve(getFileName(publicKey)).toFile().createNewFile())
        .isTrue();

    assertThat(manager.deleteGraffiti(publicKey)).isEmpty();
    checkGraffitiFile(publicKey, "");
  }

  @Test
  void deleteGraffiti_shouldThrowExceptionWhenUnableToWriteFile(@TempDir final Path tempDir)
      throws IOException {
    dataDirLayout = new SimpleDataDirLayout(tempDir);
    manager = new GraffitiManager(dataDirLayout);

    final File file = getGraffitiManagementDir().resolve(getFileName(publicKey)).toFile();
    assertThat(file.createNewFile()).isTrue();
    assertThat(file.setWritable(false)).isTrue();

    assertThat(manager.deleteGraffiti(publicKey))
        .hasValue("java.nio.file.AccessDeniedException: " + file);
  }

  private void checkGraffitiFile(final BLSPublicKey publicKey, final String graffiti) {
    final Path filePath = getGraffitiManagementDir().resolve(getFileName(publicKey));
    try {
      final Bytes32 expectedBytes = Bytes32Parser.toBytes32(graffiti);
      final Bytes32 parsedBytes = GraffitiParser.loadFromFile(filePath);
      assertThat(parsedBytes).isEqualTo(expectedBytes);
    } catch (GraffitiLoaderException e) {
      fail(e.getMessage());
    }
  }

  private Path getGraffitiManagementDir() {
    return dataDirLayout.getValidatorDataDirectory().resolve("graffiti-management");
  }

  private String getFileName(final BLSPublicKey publicKey) {
    return publicKey.toSSZBytes().toUnprefixedHexString() + ".txt";
  }
}
