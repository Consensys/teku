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

package tech.pegasys.teku.validator.client.restapi.apis;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.validator.client.KeyManager;
import tech.pegasys.teku.validator.client.restapi.apis.schema.PostKeysRequest;

public class PostKeysTest {
  private final KeyManager keyManager = mock(KeyManager.class);
  private final RestApiRequest request = mock(RestApiRequest.class);
  private final PostKeys endpoint = new PostKeys(keyManager);

  @Test
  void shouldRespondBadRequestIfPasswordsAndKeystoresMisMatch() throws JsonProcessingException {
    final PostKeysRequest body = new PostKeysRequest();
    body.setKeystores(List.of("{}"));
    body.setPasswords(List.of());
    when(request.getRequestBody()).thenReturn(body);
    endpoint.handle(request);
    verify(request)
        .respondError(
            SC_BAD_REQUEST, "Keystores count (1) and Passwords count (0) differ, cannot proceed.");
    verify(request, never()).respondOk(any());
  }

  @Test
  void shouldNotImportSlashingProtectionWithoutKeysPresent(@TempDir final Path tempDir)
      throws JsonProcessingException {
    final DataDirLayout dataDirLayout = getDataDirLayout(tempDir);
    final PostKeysRequest body = new PostKeysRequest();
    body.setSlashingProtection(Optional.of("{}"));
    when(keyManager.getDataDirLayout()).thenReturn(dataDirLayout);
    when(request.getRequestBody()).thenReturn(body);

    endpoint.handle(request);
    verify(request).respondOk(List.of());
  }

  @Test
  void emptyRequest_shouldGiveEmptySuccess() throws JsonProcessingException {
    final PostKeysRequest body = new PostKeysRequest();
    when(request.getRequestBody()).thenReturn(body);

    endpoint.handle(request);
    verify(request).respondOk(List.of());
  }

  @Test
  void shouldRespondBadRequestIfSlashingProtectionImportFails(@TempDir final Path tempDir)
      throws JsonProcessingException {
    final DataDirLayout dataDirLayout = getDataDirLayout(tempDir);
    final PostKeysRequest body = new PostKeysRequest();
    body.setSlashingProtection(Optional.of("{}"));
    body.setPasswords(List.of("pass"));
    body.setKeystores(List.of("keystore"));
    when(keyManager.getDataDirLayout()).thenReturn(dataDirLayout);
    when(request.getRequestBody()).thenReturn(body);

    assertThatThrownBy(() -> endpoint.handle(request))
        .isInstanceOf(BadRequestException.class)
        .hasMessageStartingWith("Import data does not appear to have metadata");
  }

  private DataDirLayout getDataDirLayout(final Path tempDir) {
    return new DataDirLayout() {
      @Override
      public Path getBeaconDataDirectory() {
        return null;
      }

      @Override
      public Path getValidatorDataDirectory() {
        return tempDir;
      }
    };
  }
}
