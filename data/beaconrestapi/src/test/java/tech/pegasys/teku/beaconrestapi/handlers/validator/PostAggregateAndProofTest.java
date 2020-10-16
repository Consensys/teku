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

package tech.pegasys.teku.beaconrestapi.handlers.validator;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.javalin.http.Context;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.provider.JsonProvider;

class PostAggregateAndProofTest {

  @SuppressWarnings({"unchecked", "unused"})
  private final ArgumentCaptor<SafeFuture<String>> args = ArgumentCaptor.forClass(SafeFuture.class);

  @SuppressWarnings("unused")
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();

  private final Context context = mock(Context.class);
  private final ValidatorDataProvider provider = mock(ValidatorDataProvider.class);
  private final JsonProvider jsonProvider = new JsonProvider();

  private PostAggregateAndProof handler;

  @BeforeEach
  public void beforeEach() {
    handler = new PostAggregateAndProof(provider, jsonProvider);
  }

  @Test
  public void shouldReturnBadRequestWhenRequestBodyIsInvalid() throws Exception {
    when(context.body()).thenReturn("{\"foo\": \"bar\"}");

    handler.handle(context);
    verify(context).status(SC_BAD_REQUEST);
  }

  @Test
  public void shouldReturnServerErrorWhenFutureHasUnmappedException() throws Exception {
    final SignedAggregateAndProof signedAggregateAndProof =
        dataStructureUtil.randomSignedAggregateAndProof();

    final tech.pegasys.teku.api.schema.SignedAggregateAndProof schemaSignedAggregateAndProof =
        new tech.pegasys.teku.api.schema.SignedAggregateAndProof(signedAggregateAndProof);

    when(context.body()).thenReturn(jsonProvider.objectToJSON(schemaSignedAggregateAndProof));
    doThrow(new RuntimeException()).when(provider).sendAggregateAndProofs(any());

    handler.handle(context);

    verify(context).status(SC_INTERNAL_SERVER_ERROR);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void shouldReturnSuccessWhenSendAggregateAndProofSucceeds() throws Exception {
    final SignedAggregateAndProof signedAggregateAndProof =
        dataStructureUtil.randomSignedAggregateAndProof();

    final tech.pegasys.teku.api.schema.SignedAggregateAndProof schemaSignedAggregateAndProof =
        new tech.pegasys.teku.api.schema.SignedAggregateAndProof(signedAggregateAndProof);

    String signedAggregateAndProofAsJson = jsonProvider.objectToJSON(schemaSignedAggregateAndProof);
    when(context.body()).thenReturn(signedAggregateAndProofAsJson);

    handler.handle(context);

    ArgumentCaptor<List<tech.pegasys.teku.api.schema.SignedAggregateAndProof>> captor =
        ArgumentCaptor.forClass(List.class);

    verify(provider).sendAggregateAndProofs(captor.capture());
    verify(context).status(SC_OK);

    assertThat(jsonProvider.objectToJSON(captor.getValue().get(0)))
        .isEqualTo(signedAggregateAndProofAsJson);
  }
}
