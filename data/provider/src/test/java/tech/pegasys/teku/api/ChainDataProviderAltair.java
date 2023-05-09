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

package tech.pegasys.teku.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.migrated.BlockRewardData;
import tech.pegasys.teku.api.migrated.SyncCommitteeRewardData;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.metadata.BlockAndMetaData;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class ChainDataProviderAltair extends AbstractChainDataProviderTest {

  @Test
  public void calculateRewards_shouldGetData() {
    final long reward = 5000L;
    final ChainDataProvider provider =
        new ChainDataProvider(spec, recentChainData, combinedChainDataClient);

    final Map<Integer, Integer> committeeIndices = Map.of(0, 4, 1, 2);
    final SyncCommitteeRewardData inputData = new SyncCommitteeRewardData(true, false);
    final SyncCommitteeRewardData syncCommitteeRewardData =
        provider.calculateRewards(committeeIndices, reward, data.randomBeaconBlock(), inputData);

    assertThat(syncCommitteeRewardData.isExecutionOptimistic()).isTrue();
    assertThat(syncCommitteeRewardData.isFinalized()).isFalse();
    assertThat(syncCommitteeRewardData.getRewardData())
        .containsExactlyInAnyOrder(Map.entry(2, -1 * reward), Map.entry(4, reward));
  }

  @Test
  public void getBlockRewardData_shouldGetData() {
    final Spec spec = TestSpecFactory.createMinimalAltair();
    final DataStructureUtil data = new DataStructureUtil(spec);
    final ChainDataProvider provider = setupBySpec(spec, data, 16);

    final SignedBlockAndState signedBlockAndState =
        data.randomSignedBlockAndStateWithValidatorLogic(100);
    final BlockAndMetaData blockAndMetaData =
        new BlockAndMetaData(
            signedBlockAndState.getBlock(), SpecMilestone.ALTAIR, true, false, true);

    final ObjectAndMetaData<BlockRewardData> result =
        provider.getBlockRewardData(blockAndMetaData, signedBlockAndState.getState());

    final BlockRewardData blockRewardData =
        new BlockRewardData(
            signedBlockAndState.getBlock().getProposerIndex(), 0L, 35L, 62500000L, 62500000L);
    final ObjectAndMetaData<BlockRewardData> expectedOutput =
        new ObjectAndMetaData<>(blockRewardData, SpecMilestone.ALTAIR, true, false, true);
    assertThat(result).isEqualTo(expectedOutput);
  }

  @Override
  protected Spec getSpec() {
    return TestSpecFactory.createMinimalAltair();
  }
}
