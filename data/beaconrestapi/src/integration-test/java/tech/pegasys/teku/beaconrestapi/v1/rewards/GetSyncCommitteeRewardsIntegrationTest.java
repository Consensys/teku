/*
 * Copyright ConsenSys Software Inc., 2023
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

package tech.pegasys.teku.beaconrestapi.v1.rewards;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.json.JsonUtil.serialize;

import java.io.IOException;
import java.util.List;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.migrated.SyncCommitteeRewardData;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.rewards.GetSyncCommitteeRewards;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class GetSyncCommitteeRewardsIntegrationTest
    extends AbstractDataBackedRestAPIIntegrationTest {

  @BeforeEach
  public void setup() {
    spec = TestSpecFactory.createMinimalAltair();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    startRestAPIAtGenesis(SpecMilestone.ALTAIR);
    final SyncAggregate syncAggregate =
        dataStructureUtil.randomSyncAggregate(0, 3, 4, 7, 8, 9, 10, 16, 17, 20, 23, 25, 26, 29, 30);
    final ChainBuilder.BlockOptions blockOptions =
        ChainBuilder.BlockOptions.create().setSyncAggregate(syncAggregate);
    SignedBlockAndState blockAndState = chainBuilder.generateBlockAtSlot(3, blockOptions);
    chainUpdater.saveBlock(blockAndState);
    chainUpdater.updateBestBlock(blockAndState);
  }

  @Test
  public void handleEmptyRequestBodyList() throws IOException {
    final List<String> requestBody = List.of();
    Response response =
        post(
            GetSyncCommitteeRewards.ROUTE.replace("{block_id}", "head"),
            jsonProvider.objectToJSON(requestBody));

    final SyncCommitteeRewardData data = new SyncCommitteeRewardData(false, false);
    data.increaseReward(0, 11180L);
    data.decreaseReward(1, 11180L);
    data.decreaseReward(2, 11180L);
    data.increaseReward(3, 11180L);
    data.increaseReward(4, 0L);
    data.increaseReward(5, 11180L);
    data.increaseReward(6, 0L);
    data.decreaseReward(7, 11180L);
    data.increaseReward(8, 11180L);
    data.increaseReward(9, 0L);
    data.increaseReward(10, 11180L);
    data.decreaseReward(11, 11180L);
    data.increaseReward(12, 0L);
    data.decreaseReward(13, 11180L);
    data.decreaseReward(14, 11180L);
    data.increaseReward(15, 0L);
    final String expectedResponse = serialize(data, GetSyncCommitteeRewards.RESPONSE_TYPE);

    assertThat(response.code()).isEqualTo(SC_OK);
    assertThat(response.body().string()).isEqualTo(expectedResponse);
  }

  @Test
  public void shouldGiveDecentErrorIfNoBody() throws IOException {
    Response response = post(GetSyncCommitteeRewards.ROUTE.replace("{block_id}", "head"), "");
    assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().string()).contains("Array expected but got null");
  }

  @Test
  public void shouldGiveDecentErrorIfNonNumberRequestList() throws IOException {
    final List<String> requestBody = List.of("asdf");
    Response response =
        post(
            GetSyncCommitteeRewards.ROUTE.replace("{block_id}", "head"),
            jsonProvider.objectToJSON(requestBody));

    assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().string())
        .isEqualTo(
            "{\"code\":400,\"message\":\"'asdf' is not a valid "
                + "hex encoded public key or validator index in the committee\"}");
  }

  @Test
  public void shouldReturnEmptyDataWhenOutOfValidatorRange() throws IOException {
    final List<String> requestBody = List.of("99999999999999");
    Response response =
        post(
            GetSyncCommitteeRewards.ROUTE.replace("{block_id}", "head"),
            jsonProvider.objectToJSON(requestBody));

    assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().string())
        .isEqualTo(
            "{\"code\":400,\"message\":\"'99999999999999' is not a valid "
                + "hex encoded public key or validator index in the committee\"}");
  }

  @Test
  public void shouldReturnValueOnlyForValidatorsInCommittee() throws IOException {
    final List<String> requestBody = List.of("1", "6", "31");
    Response response =
        post(
            GetSyncCommitteeRewards.ROUTE.replace("{block_id}", "head"),
            jsonProvider.objectToJSON(requestBody));

    final SyncCommitteeRewardData data = new SyncCommitteeRewardData(false, false);
    data.decreaseReward(1, 11180L);
    data.decreaseReward(6, 0L);
    final String expectedResponse = serialize(data, GetSyncCommitteeRewards.RESPONSE_TYPE);

    assertThat(response.code()).isEqualTo(SC_OK);
    assertThat(response.body().string()).isEqualTo(expectedResponse);
  }
}
