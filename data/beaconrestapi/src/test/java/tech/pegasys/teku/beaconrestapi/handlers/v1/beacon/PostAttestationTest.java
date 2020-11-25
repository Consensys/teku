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

package tech.pegasys.teku.beaconrestapi.handlers.v1.beacon;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.javalin.http.Context;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.api.schema.Attestation;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.provider.JsonProvider;

public class PostAttestationTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private Context context = mock(Context.class);
  private ValidatorDataProvider provider = mock(ValidatorDataProvider.class);
  private final JsonProvider jsonProvider = new JsonProvider();
  private PostAttestation handler;
  final Attestation attestation = new Attestation(dataStructureUtil.randomAttestation());

  @BeforeEach
  public void setup() {
    handler = new PostAttestation(provider, jsonProvider);
  }

  @Test
  void shouldBeAbleToSubmitAttestation() throws Exception {
    when(context.body()).thenReturn(jsonProvider.objectToJSON(List.of(attestation)));
    handler.handle(context);

    verify(context).status(SC_OK);
  }

  @Test
  void shouldReturnBadRequestIfAttestationInvalid() throws Exception {
    when(context.body()).thenReturn("{\"a\": \"field\"}");
    handler.handle(context);

    verify(context).status(SC_BAD_REQUEST);
  }

  @Test
  void shouldReturnBadRequestIfSingleAttestationPassed() throws Exception {
    when(context.body()).thenReturn(jsonProvider.objectToJSON(attestation));
    handler.handle(context);

    verify(context).status(SC_BAD_REQUEST);
  }
}
