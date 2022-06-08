/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.beaconrestapi.v1.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;

import java.io.IOException;
import java.util.List;
import okhttp3.Response;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.schema.altair.SyncCommitteeSubnetSubscription;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.PostSyncCommitteeSubscriptions;
import tech.pegasys.teku.spec.SpecMilestone;

public class PostSyncCommitteeSubscriptionsIntegrationTest
    extends AbstractDataBackedRestAPIIntegrationTest {

  @Test
  public void shouldReturnBadRequestWhenRequestBodyIsEmpty() throws Exception {
    startRestAPIAtGenesis(SpecMilestone.ALTAIR);
    Response response = post(PostSyncCommitteeSubscriptions.ROUTE, jsonProvider.objectToJSON(""));
    Assertions.assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
  }

  @Test
  void shouldPostSubscriptions() throws IOException {
    final List<SyncCommitteeSubnetSubscription> validators =
        List.of(new SyncCommitteeSubnetSubscription(ONE, List.of(ONE), ONE));
    startRestAPIAtGenesis(SpecMilestone.ALTAIR);
    Response response =
        post(PostSyncCommitteeSubscriptions.ROUTE, jsonProvider.objectToJSON(validators));
    assertThat(response.code()).isEqualTo(SC_OK);
  }
}
