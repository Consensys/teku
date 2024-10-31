/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.validator.remote.typedef.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;

import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.api.exceptions.RemoteServiceNotAvailableException;
import tech.pegasys.teku.ethereum.json.types.validator.SyncCommitteeSubnetSubscription;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod;
import tech.pegasys.teku.validator.remote.typedef.AbstractTypeDefRequestTestBase;

@TestSpecContext(allMilestones = true, network = Eth2Network.MINIMAL)
public class SendSubscribeToSyncCommitteeSubnetsRequestTest extends AbstractTypeDefRequestTestBase {
  private SendSubscribeToSyncCommitteeSubnetsRequest request;
  final Collection<SyncCommitteeSubnetSubscription> subscriptions =
      List.of(new SyncCommitteeSubnetSubscription(0, IntSet.of(1), UInt64.ZERO));

  @BeforeEach
  public void setup() {
    request = new SendSubscribeToSyncCommitteeSubnetsRequest(mockWebServer.url("/"), okHttpClient);
  }

  @TestTemplate
  public void postAttesterDuties_makesExpectedRequest() throws Exception {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_NO_CONTENT));
    request.submit(subscriptions);
    final RecordedRequest request = mockWebServer.takeRequest();
    assertThat(request.getMethod()).isEqualTo("POST");
    assertThat(request.getPath())
        .contains(
            ValidatorApiMethod.SUBSCRIBE_TO_SYNC_COMMITTEE_SUBNET.getPath(Collections.emptyMap()));
    assertThat(request.getBody().readUtf8())
        .isEqualTo(
            "[{\"validator_index\":\"0\",\"sync_committee_indices\":[\"1\"],\"until_epoch\":\"0\"}]");
  }

  @TestTemplate
  void handle400() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_BAD_REQUEST));
    assertThatThrownBy(() -> request.submit(subscriptions))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @TestTemplate
  void handle500() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(SC_INTERNAL_SERVER_ERROR));
    assertThatThrownBy(() -> request.submit(subscriptions))
        .isInstanceOf(RemoteServiceNotAvailableException.class);
  }
}
