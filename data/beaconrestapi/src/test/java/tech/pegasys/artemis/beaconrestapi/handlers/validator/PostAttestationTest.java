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
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.javalin.http.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.api.ValidatorDataProvider;
import tech.pegasys.artemis.api.schema.Attestation;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.storage.CombinedChainDataClient;
import tech.pegasys.artemis.validator.api.ValidatorApiChannel;
import tech.pegasys.artemis.validator.coordinator.ValidatorCoordinator;

public class PostAttestationTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private Context context = mock(Context.class);
  private ValidatorCoordinator validatorCoordinator = mock(ValidatorCoordinator.class);
  private CombinedChainDataClient combinedChainDataClient = mock(CombinedChainDataClient.class);
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private ValidatorDataProvider provider =
      new ValidatorDataProvider(validatorCoordinator, validatorApiChannel, combinedChainDataClient);
  private final JsonProvider jsonProvider = new JsonProvider();
  private Attestation attestation = new Attestation(dataStructureUtil.randomAttestation());
  private PostAttestation handler;

  @BeforeEach
  public void setup() {
    handler = new PostAttestation(provider, jsonProvider);
  }

  @Test
  void shouldBeAbleToSubmitAttestation() throws Exception {
    when(context.body()).thenReturn(jsonProvider.objectToJSON(attestation));
    handler.handle(context);

    verify(context).status(SC_NO_CONTENT);
  }

  @Test
  void shouldReturnBadRequestIfAttestationInvalid() throws Exception {
    when(context.body()).thenReturn("{\"a\": \"field\"}");
    handler.handle(context);

    verify(context).status(SC_BAD_REQUEST);
  }
}
