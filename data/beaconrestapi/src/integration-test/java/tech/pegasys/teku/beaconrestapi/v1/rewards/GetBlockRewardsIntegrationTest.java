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
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.json.JsonUtil.serialize;
import static tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata.get;

import java.io.IOException;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.migrated.BlockRewardData;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.rewards.GetBlockRewards;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class GetBlockRewardsIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {

  @BeforeEach
  public void setup() {
    spec = TestSpecFactory.createMinimalAltair();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    startRestAPIAtGenesis(SpecMilestone.ALTAIR);

    final BeaconState state = dataStructureUtil.randomBeaconState(UInt64.valueOf(5));
    final BeaconBlock block =
        dataStructureUtil
            .blockBuilder(state.getSlot().increment().longValue())
            .proposerSlashings(dataStructureUtil.randomProposerSlashings(3, 100))
            .attesterSlashings(dataStructureUtil.randomAttesterSlashings(2, 100))
            .attestations(dataStructureUtil.randomAttestations(10, state.getSlot().decrement()))
            .syncAggregate(dataStructureUtil.randomSyncAggregate(1, 2, 3, 4))
            .build()
            .getImmediately();

    final SignedBlockAndState blockAndState = dataStructureUtil.randomSignedBlockAndState(block);

    chainUpdater.saveBlock(blockAndState);
    chainUpdater.updateBestBlock(blockAndState);
  }

  @Test
  public void shouldReturnBlockRewards() throws IOException {
    Response response = get("head");

    final BlockRewardData data = new BlockRewardData(UInt64.ONE, 0L, 11970L, 0L, 0L);
    final ObjectAndMetaData<BlockRewardData> object =
        new ObjectAndMetaData<>(data, SpecMilestone.ALTAIR, false, true, false);

    final String expectedResponse = serialize(object, GetBlockRewards.RESPONSE_TYPE);

    assertThat(response.code()).isEqualTo(SC_OK);
    assertThat(response.body().string()).isEqualTo(expectedResponse);
  }

  private Response get(final String blockId) throws IOException {
    return getResponse(GetBlockRewards.ROUTE.replace("{block_id}", blockId));
  }
}
