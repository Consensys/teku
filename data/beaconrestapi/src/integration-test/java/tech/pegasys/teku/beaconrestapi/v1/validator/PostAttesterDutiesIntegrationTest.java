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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;

import java.io.IOException;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.PostAttesterDuties;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.sync.events.SyncState;

public class PostAttesterDutiesIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {

  @Test
  void shouldGiveDecentErrorIfNoBody() throws IOException {
    startRestAPIAtGenesis(SpecMilestone.ALTAIR);

    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);

    Response response = post(PostAttesterDuties.ROUTE.replace("{epoch}", "1"), "");

    assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().string()).contains("Could read request body to get required data.");
  }
}
