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

package tech.pegasys.artemis.beaconrestapi.handlers.validator;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.COMMITTEE_INDEX;
import static tech.pegasys.artemis.beaconrestapi.RestApiConstants.SLOT;

import com.google.common.primitives.UnsignedLong;
import io.javalin.http.Context;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.pegasys.artemis.api.ChainDataProvider;
import tech.pegasys.artemis.api.schema.Attestation;
import tech.pegasys.artemis.beaconrestapi.schema.BadRequest;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.provider.JsonProvider;

@ExtendWith(MockitoExtension.class)
public class GetAttestationTest {
  @Mock private Context context;
  @Mock private ChainDataProvider provider;
  private final JsonProvider jsonProvider = new JsonProvider();
  private GetAttestation handler;
  private Attestation attestation = new Attestation(DataStructureUtil.randomAttestation(1111));

  @BeforeEach
  public void setup() {
    handler = new GetAttestation(provider, jsonProvider);
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
    when(provider.isStoreAvailable()).thenReturn(false);
    handler.handle(context);

    verify(provider).isStoreAvailable();
  }

  @Test
  void shouldReturnAttestation() throws Exception {
    Map<String, List<String>> params = Map.of(SLOT, List.of("1"), COMMITTEE_INDEX, List.of("1"));

    when(context.queryParamMap()).thenReturn(params);
    when(provider.isStoreAvailable()).thenReturn(true);
    when(provider.getUnsignedAttestationAtSlot(UnsignedLong.ONE, 1))
        .thenReturn(Optional.of(attestation));
    handler.handle(context);

    verify(provider).isStoreAvailable();
    verify(context).result(jsonProvider.objectToJSON(attestation));
  }

  @Test
  void shouldReturnNoAttestationIfNotFound() throws Exception {
    Map<String, List<String>> params = Map.of(SLOT, List.of("1"), COMMITTEE_INDEX, List.of("1"));

    when(context.queryParamMap()).thenReturn(params);
    when(provider.isStoreAvailable()).thenReturn(true);
    when(provider.getUnsignedAttestationAtSlot(UnsignedLong.ONE, 1)).thenReturn(Optional.empty());
    handler.handle(context);

    verify(provider).isStoreAvailable();
    verify(context).status(SC_NOT_FOUND);
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
