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

package tech.pegasys.teku.validator.client.restapi.apis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_FORBIDDEN;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_UNAUTHORIZED;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.http.HttpErrorResponse;
import tech.pegasys.teku.infrastructure.restapi.StubRestApiRequest;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.signatures.Signer;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.Bytes32Parser;
import tech.pegasys.teku.validator.client.OwnedKeyManager;
import tech.pegasys.teku.validator.client.Validator;

class GetGraffitiTest {
  private final OwnedKeyManager keyManager = mock(OwnedKeyManager.class);
  private final GetGraffiti handler = new GetGraffiti(keyManager);
  private StubRestApiRequest request;

  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());

  @Test
  void shouldGetGraffiti() throws JsonProcessingException {
    final BLSPublicKey publicKey = dataStructureUtil.randomPublicKey();
    final String stringGraffiti = "Test graffiti";
    final Bytes32 graffiti = Bytes32Parser.toBytes32(stringGraffiti);

    request =
        StubRestApiRequest.builder()
            .metadata(handler.getMetadata())
            .pathParameter("pubkey", publicKey.toHexString())
            .build();

    final Validator validator =
        new Validator(publicKey, mock(Signer.class), () -> Optional.of(graffiti));
    when(keyManager.getValidatorByPublicKey(eq(publicKey))).thenReturn(Optional.of(validator));

    handler.handleRequest(request);

    final GetGraffiti.GraffitiResponse expectedResponse =
        new GetGraffiti.GraffitiResponse(publicKey, stringGraffiti);
    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(expectedResponse);
  }

  @Test
  void shouldGetEmptyGraffiti() throws JsonProcessingException {
    final BLSPublicKey publicKey = dataStructureUtil.randomPublicKey();
    request =
        StubRestApiRequest.builder()
            .metadata(handler.getMetadata())
            .pathParameter("pubkey", publicKey.toHexString())
            .build();

    final Validator validator = new Validator(publicKey, mock(Signer.class), Optional::empty);
    when(keyManager.getValidatorByPublicKey(eq(publicKey))).thenReturn(Optional.of(validator));

    handler.handleRequest(request);

    final GetGraffiti.GraffitiResponse expectedResponse =
        new GetGraffiti.GraffitiResponse(publicKey, "");
    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(expectedResponse);
  }

  @Test
  void shouldHandleValidatorNotFound() throws IOException {
    request =
        StubRestApiRequest.builder()
            .metadata(handler.getMetadata())
            .pathParameter("pubkey", dataStructureUtil.randomPublicKey().toHexString())
            .build();

    when(keyManager.getValidatorByPublicKey(any())).thenReturn(Optional.empty());

    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_NOT_FOUND);
    assertThat(request.getResponseBody())
        .isEqualTo(new HttpErrorResponse(SC_NOT_FOUND, "Validator not found"));
  }

  @Test
  void metadata_shouldHandle200() throws JsonProcessingException {
    final GetGraffiti.GraffitiResponse response =
        new GetGraffiti.GraffitiResponse(dataStructureUtil.randomPublicKey(), "Test graffiti");
    final String responseData = getResponseStringFromMetadata(handler, SC_OK, response);
    assertThat(responseData)
        .isEqualTo(
            "{\"data\":{\"pubkey\":"
                + "\"0xa4654ac3105a58c7634031b5718c4880c87300f72091cfbc69fe490b71d93a671e00e80a388e1ceb8ea1de112003e976\","
                + "\"graffiti\":\"Test graffiti\"}}");
  }

  @Test
  void metadata_shouldHandle400() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_BAD_REQUEST);
  }

  @Test
  void metadata_shouldHandle401() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_UNAUTHORIZED);
  }

  @Test
  void metadata_shouldHandle403() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_FORBIDDEN);
  }

  @Test
  void metadata_shouldHandle500() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_INTERNAL_SERVER_ERROR);
  }
}
