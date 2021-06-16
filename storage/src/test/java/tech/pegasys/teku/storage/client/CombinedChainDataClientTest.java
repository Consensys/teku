/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.teku.storage.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.CommitteeAssignment;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.api.StorageQueryChannel;

/** Note: Most tests should be added to the integration-test directory */
class CombinedChainDataClientTest {
  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final StorageQueryChannel historicalChainData = mock(StorageQueryChannel.class);
  private final CombinedChainDataClient client =
      new CombinedChainDataClient(recentChainData, historicalChainData, spec);

  final HashSet<SignedBeaconBlock> nonCanonicalBlocks = new HashSet<>();
  final SignedBeaconBlock firstBlock = dataStructureUtil.randomSignedBeaconBlock(1);
  final SignedBeaconBlock secondBlock = dataStructureUtil.randomSignedBeaconBlock(1);

  @Test
  public void getCommitteesFromStateWithCache_shouldReturnCommitteeAssignments() {
    BeaconState state = dataStructureUtil.randomBeaconState();
    List<CommitteeAssignment> data =
        client.getCommitteesFromState(state, spec.getCurrentEpoch(state));
    assertThat(data.size()).isEqualTo(spec.getSlotsPerEpoch(state.getSlot()));
  }

  @Test
  public void mergeNonCanonicalAndCanonicalBlocks_shouldAddCanonicalBlockIfPresent() {
    nonCanonicalBlocks.add(firstBlock);
    assertThat(
            client.mergeNonCanonicalAndCanonicalBlocks(
                nonCanonicalBlocks, Optional.of(secondBlock)))
        .containsExactlyInAnyOrder(firstBlock, secondBlock);
  }

  @Test
  public void mergeNonCanonicalAndCanonicalBlocks_shouldReturnNonCanonicalOnly() {
    nonCanonicalBlocks.add(firstBlock);
    nonCanonicalBlocks.add(secondBlock);
    assertThat(client.mergeNonCanonicalAndCanonicalBlocks(nonCanonicalBlocks, Optional.empty()))
        .containsExactlyInAnyOrder(firstBlock, secondBlock);
  }

  @Test
  public void mergeNonCanonicalAndCanonicalBlocks_shouldReturnCanonicalOnly() {
    assertThat(
            client.mergeNonCanonicalAndCanonicalBlocks(
                nonCanonicalBlocks, Optional.of(secondBlock)))
        .containsExactlyInAnyOrder(secondBlock);
  }
}
