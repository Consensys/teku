/*
 * Copyright Consensys Software Inc., 2023
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

package tech.pegasys.teku.spec.datastructures.metadata;

import static tech.pegasys.teku.spec.constants.EthConstants.GWEI_TO_WEI;

import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.StateTransitionCaches;

public record BlockContainerAndMetaData(
    BlockContainer blockContainer,
    SpecMilestone specMilestone,
    UInt256 executionPayloadValue,
    UInt256 consensusBlockValue) {

  public static BlockContainerAndMetaData fromBeaconBlockAndState(
      final BeaconBlockAndState beaconBlockAndState, final Spec spec) {
    final StateTransitionCaches stateTransitionCaches =
        BeaconStateCache.getStateTransitionCaches(beaconBlockAndState.getState());

    return new BlockContainerAndMetaData(
        beaconBlockAndState.getBlock(),
        spec.atSlot(beaconBlockAndState.getSlot()).getMilestone(),
        stateTransitionCaches.getLastBlockExecutionValue(),
        GWEI_TO_WEI.multiply(stateTransitionCaches.getLastBlockRewards().longValue()));
  }

  public static BlockContainerAndMetaData fromBlockContainer(
      final BlockContainer blockContainer, final Spec spec) {
    return new BlockContainerAndMetaData(
        blockContainer,
        spec.atSlot(blockContainer.getSlot()).getMilestone(),
        UInt256.ZERO,
        UInt256.ZERO);
  }

  public static BlockContainerAndMetaData fromBlockContainer(
      final BlockContainer blockContainer, final SpecMilestone specMilestone) {
    return new BlockContainerAndMetaData(blockContainer, specMilestone, UInt256.ZERO, UInt256.ZERO);
  }

  public BlockContainerAndMetaData withBlockContainer(final BlockContainer blockContainer) {
    return new BlockContainerAndMetaData(
        blockContainer, specMilestone, executionPayloadValue, consensusBlockValue);
  }
}
