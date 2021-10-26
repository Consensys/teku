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
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import okhttp3.Response;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.response.v1.validator.PostSyncDutiesResponse;
import tech.pegasys.teku.api.schema.BLSPubKey;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.validator.PostSyncDuties;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.sync.events.SyncState;
import tech.pegasys.teku.validator.api.SyncCommitteeDuties;
import tech.pegasys.teku.validator.api.SyncCommitteeDuty;

public class PostSyncDutiesIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {
  final List<Integer> validators = List.of(1);

  @Test
  public void shouldReturnBadRequestWhenRequestBodyIsEmpty() throws Exception {
    startRestAPIAtGenesis(SpecMilestone.ALTAIR);
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);
    Response response =
        post(PostSyncDuties.ROUTE.replace("{epoch}", "1"), jsonProvider.objectToJSON(""));
    Assertions.assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
  }

  @Test
  void shouldGetSyncCommitteeDuties() throws IOException {
    startRestAPIAtGenesis(SpecMilestone.ALTAIR);

    final SafeFuture<Optional<SyncCommitteeDuties>> out =
        SafeFuture.completedFuture(
            Optional.of(
                new SyncCommitteeDuties(
                    List.of(
                        new SyncCommitteeDuty(
                            VALIDATOR_KEYS.get(1).getPublicKey(), 1, Set.of(11))))));
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);
    when(validatorApiChannel.getSyncCommitteeDuties(ONE, validators)).thenReturn(out);

    Response response =
        post(PostSyncDuties.ROUTE.replace("{epoch}", "1"), jsonProvider.objectToJSON(validators));

    Assertions.assertThat(response.code()).isEqualTo(SC_OK);
    final PostSyncDutiesResponse dutiesResponse =
        jsonProvider.jsonToObject(response.body().string(), PostSyncDutiesResponse.class);
    assertThat(dutiesResponse.data.get(0))
        .isEqualTo(
            new tech.pegasys.teku.api.response.v1.validator.SyncCommitteeDuty(
                new BLSPubKey(VALIDATOR_KEYS.get(1).getPublicKey()), ONE, Set.of(11)));
  }
}
