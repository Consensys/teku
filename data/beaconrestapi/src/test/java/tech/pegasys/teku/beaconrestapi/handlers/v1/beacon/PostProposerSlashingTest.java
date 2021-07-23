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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.javalin.http.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.NodeDataProvider;
import tech.pegasys.teku.api.schema.ProposerSlashing;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class PostProposerSlashingTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private Context context = mock(Context.class);
  private NodeDataProvider provider = mock(NodeDataProvider.class);
  private final JsonProvider jsonProvider = new JsonProvider();
  private PostProposerSlashing handler;

  @BeforeEach
  public void setup() {
    handler = new PostProposerSlashing(provider, jsonProvider);
  }

  @Test
  void shouldBeAbleToSubmitSlashing() throws Exception {
    final ProposerSlashing slashing =
        new ProposerSlashing(dataStructureUtil.randomProposerSlashing());
    when(context.body()).thenReturn(jsonProvider.objectToJSON(slashing));
    when(provider.postProposerSlashing(slashing))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));
    handler.handle(context);

    verify(provider).postProposerSlashing(slashing);
    verify(context).status(SC_OK);
  }

  @Test
  void shouldReturnBadRequestIfProposerSlashingIsInvalid() throws Exception {
    final ProposerSlashing slashing =
        new ProposerSlashing(dataStructureUtil.randomProposerSlashing());
    when(context.body()).thenReturn(jsonProvider.objectToJSON(slashing));
    when(provider.postProposerSlashing(slashing))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.reject("Oops")));
    handler.handle(context);

    verify(provider).postProposerSlashing(slashing);
    verify(context).status(SC_BAD_REQUEST);
  }

  @Test
  void shouldReturnBadRequestIfProposerSlashingIsIncorrectlyFormatted() throws Exception {
    when(context.body()).thenReturn("{\"a\": \"field\"}");
    handler.handle(context);

    verify(provider, never()).postProposerSlashing(any());
    verify(context).status(SC_BAD_REQUEST);
  }
}
