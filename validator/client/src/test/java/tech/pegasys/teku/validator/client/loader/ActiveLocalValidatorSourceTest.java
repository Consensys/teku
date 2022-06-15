/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.validator.client.loader;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeleteKeyResult;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeletionStatus;

class ActiveLocalValidatorSourceTest {

  @Test
  void shouldDeleteStorage(@TempDir final Path tempDir) throws IOException {
    final ActiveLocalValidatorSource source = createActiveValidatorSource(tempDir);
    final DeleteKeyResult result = source.delete();
    assertThat(result.getStatus()).isEqualTo(DeletionStatus.DELETED);
    assertThat(source.getKeystorePath().toFile()).doesNotExist();
    assertThat(source.getPasswordPath().toFile()).doesNotExist();
  }

  @Test
  void shouldReportError(@TempDir final Path tempDir) throws IOException {
    final ActiveLocalValidatorSource source = createActiveValidatorSource(tempDir);
    assumeTrue(tempDir.toFile().setReadOnly());

    DeleteKeyResult result = source.delete();
    assertThat(result.getStatus()).isEqualTo(DeletionStatus.ERROR);
    assertThat(result.getMessage().orElse("")).startsWith("Failed to delete file");
  }

  private ActiveLocalValidatorSource createActiveValidatorSource(final Path tempDir)
      throws IOException {
    final Path keystore = tempDir.resolve("a.json");
    final Path password = tempDir.resolve("a.txt");
    Files.createFile(keystore);
    Files.createFile(password);
    assertThat(keystore.toFile()).isFile();
    assertThat(password.toFile()).isFile();

    return new ActiveLocalValidatorSource(keystore, password);
  }
}
