/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.storage.api;

import static com.google.common.base.Preconditions.checkArgument;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.BlockAndCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class StorageUpdate {

  private final Optional<UInt64> genesisTime;
  private final Optional<FinalizedChainData> finalizedChainData;
  private final Optional<Checkpoint> justifiedCheckpoint;
  private final Optional<Checkpoint> bestJustifiedCheckpoint;
  private final Map<Bytes32, SlotAndBlockRoot> stateRoots;
  private final Map<Bytes32, BlockAndCheckpoints> hotBlocks;
  private final Map<Bytes32, BeaconState> hotStates;
  private final Map<Bytes32, UInt64> deletedHotBlocks;
  private final boolean optimisticTransitionBlockRootSet;
  private final Optional<Bytes32> optimisticTransitionBlockRoot;
  private final boolean blobsSidecarEnabled;
  private final boolean isEmpty;

  public StorageUpdate(
      final Optional<UInt64> genesisTime,
      final Optional<FinalizedChainData> finalizedChainData,
      final Optional<Checkpoint> justifiedCheckpoint,
      final Optional<Checkpoint> bestJustifiedCheckpoint,
      final Map<Bytes32, BlockAndCheckpoints> hotBlocks,
      final Map<Bytes32, BeaconState> hotStates,
      final Map<Bytes32, UInt64> deletedHotBlocks,
      final Map<Bytes32, SlotAndBlockRoot> stateRoots,
      final boolean optimisticTransitionBlockRootSet,
      final Optional<Bytes32> optimisticTransitionBlockRoot,
      @NonUpdating final boolean blobsSidecarEnabled) {
    this.genesisTime = genesisTime;
    this.finalizedChainData = finalizedChainData;
    this.justifiedCheckpoint = justifiedCheckpoint;
    this.bestJustifiedCheckpoint = bestJustifiedCheckpoint;
    this.hotBlocks = hotBlocks;
    this.hotStates = hotStates;
    this.deletedHotBlocks = deletedHotBlocks;
    this.stateRoots = stateRoots;
    this.optimisticTransitionBlockRootSet = optimisticTransitionBlockRootSet;
    this.optimisticTransitionBlockRoot = optimisticTransitionBlockRoot;
    this.blobsSidecarEnabled = blobsSidecarEnabled;
    checkArgument(
        optimisticTransitionBlockRootSet || optimisticTransitionBlockRoot.isEmpty(),
        "Can't have optimisticTransitionBlockRoot present but not set");

    this.isEmpty =
        genesisTime.isEmpty()
            && justifiedCheckpoint.isEmpty()
            && finalizedChainData.isEmpty()
            && bestJustifiedCheckpoint.isEmpty()
            && hotBlocks.isEmpty()
            && hotStates.isEmpty()
            && deletedHotBlocks.isEmpty()
            && stateRoots.isEmpty()
            && !optimisticTransitionBlockRootSet;
  }

  public boolean isEmpty() {
    return isEmpty;
  }

  public Optional<UInt64> getGenesisTime() {
    return genesisTime;
  }

  public Optional<Checkpoint> getJustifiedCheckpoint() {
    return justifiedCheckpoint;
  }

  public Optional<Checkpoint> getFinalizedCheckpoint() {
    return finalizedChainData.map(FinalizedChainData::getFinalizedCheckpoint);
  }

  public Optional<Checkpoint> getBestJustifiedCheckpoint() {
    return bestJustifiedCheckpoint;
  }

  public Map<Bytes32, BlockAndCheckpoints> getHotBlocks() {
    return hotBlocks;
  }

  public Map<Bytes32, BeaconState> getHotStates() {
    return hotStates;
  }

  public Map<Bytes32, UInt64> getDeletedHotBlocks() {
    return deletedHotBlocks;
  }

  public Map<Bytes32, Bytes32> getFinalizedChildToParentMap() {
    return finalizedChainData
        .map(FinalizedChainData::getFinalizedChildToParentMap)
        .orElse(Collections.emptyMap());
  }

  public Map<Bytes32, SignedBeaconBlock> getFinalizedBlocks() {
    return finalizedChainData.map(FinalizedChainData::getBlocks).orElse(Collections.emptyMap());
  }

  public Map<Bytes32, BeaconState> getFinalizedStates() {
    return finalizedChainData.map(FinalizedChainData::getStates).orElse(Collections.emptyMap());
  }

  public Optional<BeaconState> getLatestFinalizedState() {
    return finalizedChainData.map(FinalizedChainData::getLatestFinalizedState);
  }

  public boolean isFinalizedOptimisticTransitionBlockRootSet() {
    return optimisticTransitionBlockRootSet;
  }

  public Optional<Bytes32> getOptimisticTransitionBlockRoot() {
    return optimisticTransitionBlockRoot;
  }

  public Map<Bytes32, SlotAndBlockRoot> getStateRoots() {
    return stateRoots;
  }

  public boolean isBlobsSidecarEnabled() {
    return blobsSidecarEnabled;
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.PARAMETER)
  @interface NonUpdating {}
}
