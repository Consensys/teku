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
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.javalin.http.Context;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.api.request.v1.validator.BeaconCommitteeSubscriptionRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;

class PostSubscribeToBeaconCommitteeSubnetTest {

  private final Context context = mock(Context.class);
  private final ValidatorDataProvider provider = mock(ValidatorDataProvider.class);
  private final JsonProvider jsonProvider = new JsonProvider();

  private final PostSubscribeToBeaconCommitteeSubnet handler =
      new PostSubscribeToBeaconCommitteeSubnet(provider, jsonProvider);

  @Test
  public void shouldReturnBadRequestWhenRequestBodyIsInvalid() throws Exception {
    when(context.body()).thenReturn("{\"foo\": \"bar\"}");

    handler.handle(context);
    verify(context).status(SC_BAD_REQUEST);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void shouldReturnSuccessWhenSubscriptionToBeaconCommitteeIsSuccessful() throws Exception {
    final BeaconCommitteeSubscriptionRequest request1 =
        new BeaconCommitteeSubscriptionRequest(1, 2, UInt64.ZERO, UInt64.ONE, true);
    final BeaconCommitteeSubscriptionRequest request2 =
        new BeaconCommitteeSubscriptionRequest(3, 4, UInt64.ZERO, UInt64.ONE, false);

    final String requestJson = jsonProvider.objectToJSON(List.of(request1, request2));
    when(context.body()).thenReturn(requestJson);

    handler.handle(context);

    ArgumentCaptor<List<BeaconCommitteeSubscriptionRequest>> captor =
        ArgumentCaptor.forClass(List.class);

    verify(provider).subscribeToBeaconCommittee(captor.capture());
    verify(context).status(SC_OK);

    List<BeaconCommitteeSubscriptionRequest> receivedRequest = captor.getValue();
    assertThat(receivedRequest).usingRecursiveComparison().isEqualTo(List.of(request1, request2));
  }
}
