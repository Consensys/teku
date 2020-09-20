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
import static org.mockito.ArgumentMatchers.anyList;
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
import tech.pegasys.teku.api.schema.SubnetSubscription;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;

class PostSubscribeToPersistentSubnetsTest {

  private final Context context = mock(Context.class);
  private final ValidatorDataProvider provider = mock(ValidatorDataProvider.class);
  private final JsonProvider jsonProvider = new JsonProvider();

  private PostSubscribeToPersistentSubnets handler;

  @BeforeEach
  public void beforeEach() {
    handler = new PostSubscribeToPersistentSubnets(provider, jsonProvider);
  }

  @Test
  public void shouldReturnBadRequestWhenRequestBodyIsInvalid() throws Exception {
    when(context.body()).thenReturn("{\"foo\": \"bar\"}");

    handler.handle(context);
    verify(context).status(SC_BAD_REQUEST);
  }

  @Test
  public void shouldReturnServerErrorWhenProviderThrowsUnmappedException() throws Exception {
    final List<SubnetSubscription> request = List.of(new SubnetSubscription(1, UInt64.ONE));

    when(context.body()).thenReturn(jsonProvider.objectToJSON(request));
    doThrow(new RuntimeException()).when(provider).subscribeToPersistentSubnets(anyList());

    handler.handle(context);

    verify(context).status(SC_INTERNAL_SERVER_ERROR);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldReturnSuccessWhenSubscriptionToBeaconCommitteeIsSuccessful() throws Exception {
    final List<SubnetSubscription> request = List.of(new SubnetSubscription(1, UInt64.ONE));

    final String requestJson = jsonProvider.objectToJSON(request);
    when(context.body()).thenReturn(requestJson);

    handler.handle(context);

    ArgumentCaptor<List<SubnetSubscription>> captor = ArgumentCaptor.forClass(List.class);

    verify(provider).subscribeToPersistentSubnets(captor.capture());
    verify(context).status(SC_OK);

    List<SubnetSubscription> receivedRequest = captor.getValue();
    assertThat(receivedRequest).usingRecursiveComparison().isEqualTo(request);
  }
}
