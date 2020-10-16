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

package tech.pegasys.teku.beaconrestapi.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.request.SubscribeToBeaconCommitteeRequest;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.validator.PostSubscribeToBeaconCommittee;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class PostSubscribeToPersistentSubnetsIntegrationTest
    extends AbstractDataBackedRestAPIIntegrationTest {

  @BeforeEach
  public void setup() {
    startRestAPIAtGenesis();
  }

  @Test
  public void shouldReturnBadRequestWhenRequestBodyIsInvalid() throws Exception {
    Response response =
        post(PostSubscribeToBeaconCommittee.ROUTE, jsonProvider.objectToJSON("{\"foo\": \"bar\"}"));
    assertThat(response.code()).isEqualTo(400);
  }

  @Test
  public void shouldReturnServerErrorWhenUnexpectedErrorHappens() throws Exception {
    final SubscribeToBeaconCommitteeRequest request =
        new SubscribeToBeaconCommitteeRequest(1, UInt64.ONE);

    doThrow(new RuntimeException()).when(validatorApiChannel).subscribeToBeaconCommittee(any());

    Response response =
        post(PostSubscribeToBeaconCommittee.ROUTE, jsonProvider.objectToJSON(request));
    assertThat(response.code()).isEqualTo(500);
  }

  @Test
  public void shouldReturnSuccessWhenRequestBodyIsValid() throws Exception {
    final SubscribeToBeaconCommitteeRequest request =
        new SubscribeToBeaconCommitteeRequest(1, UInt64.ONE);

    Response response =
        post(PostSubscribeToBeaconCommittee.ROUTE, jsonProvider.objectToJSON(request));

    assertThat(response.code()).isEqualTo(200);
  }
}
