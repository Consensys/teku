/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.infrastructure.restapi;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.javalin.Javalin;
import java.io.IOException;
import java.net.BindException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;

class RestApiTest {
  private final Javalin app = mock(Javalin.class);

  private final RestApi restApi = new RestApi(app, Optional.empty(), Optional.empty());

  @Test
  void start_shouldThrowInvalidConfigurationExceptionWhenPortInUse() {
    when(app.start()).thenThrow(new RuntimeException("Oh no", new BindException("Port in use")));
    assertThatThrownBy(restApi::start).isInstanceOf(InvalidConfigurationException.class);
    assertThat(restApi.getRestApiDocs()).isEmpty();
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void start_shouldFailFastWhenTokenNotWritable(@TempDir final Path tempDir) throws IOException {

    final Path managerDir = tempDir.resolve("manager");
    Files.createDirectory(
        managerDir,
        PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("--x--x---")));

    final RestApi restApi =
        new RestApi(app, Optional.empty(), Optional.of(managerDir.resolve("pass")));
    assertThatThrownBy(restApi::start).isInstanceOf(IllegalStateException.class);
    assertThat(restApi.getRestApiDocs()).isEmpty();
  }
}
