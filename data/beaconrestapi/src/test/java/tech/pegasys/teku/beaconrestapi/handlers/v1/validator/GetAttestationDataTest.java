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

package tech.pegasys.teku.beaconrestapi.handlers.v1.validator;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.COMMITTEE_INDEX;
import static tech.pegasys.teku.beaconrestapi.RestApiConstants.SLOT;

import io.javalin.http.Context;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.api.response.v1.validator.GetAttestationDataResponse;
import tech.pegasys.teku.api.schema.Attestation;
import tech.pegasys.teku.beaconrestapi.schema.BadRequest;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.storage.client.ChainDataUnavailableException;

class GetAttestationDataTest {

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private Context context = mock(Context.class);
  private ValidatorDataProvider provider = mock(ValidatorDataProvider.class);
  private final JsonProvider jsonProvider = new JsonProvider();
  private GetAttestationData handler;
  private Attestation attestation = new Attestation(dataStructureUtil.randomAttestation());

  @SuppressWarnings("unchecked")
  final ArgumentCaptor<SafeFuture<String>> resultCaptor = ArgumentCaptor.forClass(SafeFuture.class);

  @BeforeEach
  public void setup() {
    handler = new GetAttestationData(provider, jsonProvider);
  }

  @Test
  void shouldRejectTooFewArguments() throws Exception {
    badRequestParamsTest(Map.of(), "Please specify both slot and committee_index");
  }

  @Test
  void shouldRejectWithoutSlot() throws Exception {
    badRequestParamsTest(
        Map.of("foo", List.of(), "Foo2", List.of()), "'slot' cannot be null or empty.");
  }

  @Test
  void shouldRejectWithoutCommitteeIndex() throws Exception {
    badRequestParamsTest(
        Map.of(SLOT, List.of("1"), "Foo2", List.of()),
        "'committee_index' cannot be null or empty.");
  }

  @Test
  void shouldRejectNegativeCommitteeIndex() throws Exception {
    badRequestParamsTest(
        Map.of(SLOT, List.of("1"), COMMITTEE_INDEX, List.of("-1")),
        "'committee_index' needs to be greater than or equal to 0.");
  }

  @Test
  void shouldReturnNoContentIfNotReady() throws Exception {
    Map<String, List<String>> params = Map.of(SLOT, List.of("1"), COMMITTEE_INDEX, List.of("1"));

    when(context.queryParamMap()).thenReturn(params);
    when(provider.createUnsignedAttestationAtSlot(UInt64.ONE, 1))
        .thenReturn(SafeFuture.failedFuture(new ChainDataUnavailableException()));
    handler.handle(context);

    verify(context).result(resultCaptor.capture());
    final SafeFuture<String> result = resultCaptor.getValue();
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::join).hasRootCauseInstanceOf(ChainDataUnavailableException.class);
  }

  @Test
  void shouldReturnAttestationData() throws Exception {
    Map<String, List<String>> params = Map.of(SLOT, List.of("1"), COMMITTEE_INDEX, List.of("1"));

    when(context.queryParamMap()).thenReturn(params);
    when(provider.isStoreAvailable()).thenReturn(true);
    when(provider.createUnsignedAttestationAtSlot(UInt64.ONE, 1))
        .thenReturn(SafeFuture.completedFuture(Optional.of(attestation)));
    handler.handle(context);

    verify(context).result(resultCaptor.capture());
    final SafeFuture<String> result = resultCaptor.getValue();
    assertThat(result)
        .isCompletedWithValue(
            jsonProvider.objectToJSON(new GetAttestationDataResponse(attestation.data)));
  }

  private void badRequestParamsTest(final Map<String, List<String>> params, String message)
      throws Exception {
    when(context.queryParamMap()).thenReturn(params);

    handler.handle(context);
    verify(context).status(SC_BAD_REQUEST);

    if (StringUtils.isNotEmpty(message)) {
      BadRequest badRequest = new BadRequest(message);
      verify(context).result(jsonProvider.objectToJSON(badRequest));
    }
  }
}
