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

package tech.pegasys.teku.validator.client.restapi.apis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_FORBIDDEN;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_UNAUTHORIZED;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;
import static tech.pegasys.teku.spec.generator.signatures.NoOpLocalSigner.NO_OP_SIGNER;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.validator.client.ActiveKeyManager;
import tech.pegasys.teku.validator.client.Validator;

class GetKeysTest {
  private final ActiveKeyManager keyManager = Mockito.mock(ActiveKeyManager.class);
  private final RestApiRequest request = mock(RestApiRequest.class);
  private final GetKeys handler = new GetKeys(keyManager);

  @Test
  void shouldListValidatorKeys() throws Exception {
    final List<Validator> activeValidatorList = getValidatorList();
    when(keyManager.getActiveValidatorKeys()).thenReturn(activeValidatorList);
    handler.handleRequest(request);

    verify(request).respondOk(activeValidatorList);
  }

  @Test
  void shouldListEmptyValidatorKeys() throws Exception {
    final List<Validator> activeValidatorList = Collections.emptyList();
    when(keyManager.getActiveValidatorKeys()).thenReturn(activeValidatorList);
    handler.handleRequest(request);

    verify(request).respondOk(activeValidatorList);
  }

  @Test
  void metadata_shouldHandle200() throws JsonProcessingException {
    final List<Validator> validators = getValidatorList();
    final String responseData = getResponseStringFromMetadata(handler, SC_OK, validators);
    assertThat(responseData)
        .isEqualTo(
            "{\"data\":[{\"validating_pubkey\":\""
                + validators.get(0).getPublicKey()
                + "\",\"readonly\":true},"
                + "{\"validating_pubkey\":\""
                + validators.get(1).getPublicKey()
                + "\",\"readonly\":false}]}");
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

  private List<Validator> getValidatorList() {
    BLSKeyPair keyPair1 = BLSTestUtil.randomKeyPair(1);
    BLSKeyPair keyPair2 = BLSTestUtil.randomKeyPair(2);
    Validator validator1 =
        new Validator(keyPair1.getPublicKey(), NO_OP_SIGNER, Optional::empty, true);
    Validator validator2 =
        new Validator(keyPair2.getPublicKey(), NO_OP_SIGNER, Optional::empty, false);
    return Arrays.asList(validator1, validator2);
  }
}
