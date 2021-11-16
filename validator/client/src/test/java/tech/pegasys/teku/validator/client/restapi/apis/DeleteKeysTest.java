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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.validator.client.restapi.apis.schema.DeleteKeyResult.success;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.restapi.exceptions.MissingRequestBodyException;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.client.KeyManager;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeleteKeysRequest;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeleteKeysResponse;

public class DeleteKeysTest {
  final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createMinimalAltair());
  private final KeyManager keyManager = mock(KeyManager.class);
  private final DeleteKeys endpoint = new DeleteKeys(keyManager);

  private final DeleteKeysRequest requestData = new DeleteKeysRequest();
  private final RestApiRequest request = mock(RestApiRequest.class);

  @Test
  void shouldCallKeyManagerWithListOfKeys() throws JsonProcessingException {
    final List<BLSPublicKey> keys =
        List.of(dataStructureUtil.randomPublicKey(), dataStructureUtil.randomPublicKey());
    requestData.setPublicKeys(keys);
    when(keyManager.deleteValidators(eq(keys)))
        .thenReturn(new DeleteKeysResponse(List.of(success(), success()), ""));
    when(request.getRequestBody()).thenReturn(requestData);

    endpoint.handle(request);
    verify(keyManager).deleteValidators(keys);
    verify(request, never()).respondError(anyInt(), any());
    verify(request, times(1)).respondOk(any(DeleteKeysResponse.class));
  }

  @Test
  void shouldReturnSuccessIfNoKeysArePassed() throws JsonProcessingException {
    when(request.getRequestBody()).thenReturn(requestData);
    when(keyManager.deleteValidators(any())).thenReturn(new DeleteKeysResponse(List.of(), ""));

    endpoint.handle(request);
    verify(keyManager).deleteValidators(eq(Collections.emptyList()));
    verify(request, never()).respondError(anyInt(), any());
    verify(request, times(1)).respondOk(any(DeleteKeysResponse.class));
  }

  @Test
  void shouldReturnBadRequest() throws JsonProcessingException {
    when(request.getRequestBody()).thenThrow(new MissingRequestBodyException());

    assertThatThrownBy(() -> endpoint.handle(request))
        .isInstanceOf(MissingRequestBodyException.class);
    verify(keyManager, never()).deleteValidators(any());
    verify(request, never()).respondOk(any());
  }
}
