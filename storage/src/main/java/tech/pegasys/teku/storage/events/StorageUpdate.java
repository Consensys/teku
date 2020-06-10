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

package tech.pegasys.teku.storage.events;

import com.google.common.primitives.UnsignedLong;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;

public class StorageUpdate {

  private final Optional<UnsignedLong> genesisTime;
  private final Optional<Checkpoint> justifiedCheckpoint;
  private final Optional<Checkpoint> finalizedCheckpoint;
  private final Optional<Checkpoint> bestJustifiedCheckpoint;
  private final Optional<BeaconState> latestFinalizedState;
  private final Map<Bytes32, SignedBeaconBlock> hotBlocks;
  private final Set<SignedBeaconBlock> finalizedBlocks;
  private final Map<Bytes32, BeaconState> finalizedStates;
  private final Map<Checkpoint, BeaconState> checkpointStates;
  private final Map<UnsignedLong, VoteTracker> votes;
  private final Set<Checkpoint> deletedCheckpointStates;
  private final Set<Bytes32> deletedHotBlocks;

  public StorageUpdate(
      final Optional<UnsignedLong> genesisTime,
      final Optional<Checkpoint> justifiedCheckpoint,
      final Optional<Checkpoint> finalizedCheckpoint,
      final Optional<Checkpoint> bestJustifiedCheckpoint,
      final Optional<BeaconState> latestFinalizedState,
      final Map<Bytes32, SignedBeaconBlock> hotBlocks,
      final Set<Bytes32> deletedHotBlocks,
      final Set<SignedBeaconBlock> finalizedBlocks,
      final Map<Bytes32, BeaconState> finalizedStates,
      final Map<Checkpoint, BeaconState> checkpointStates,
      final Set<Checkpoint> deletedCheckpointStates,
      final Map<UnsignedLong, VoteTracker> votes) {
    this.genesisTime = genesisTime;
    this.justifiedCheckpoint = justifiedCheckpoint;
    this.finalizedCheckpoint = finalizedCheckpoint;
    this.bestJustifiedCheckpoint = bestJustifiedCheckpoint;
    this.latestFinalizedState = latestFinalizedState;
    this.hotBlocks = hotBlocks;
    this.deletedHotBlocks = deletedHotBlocks;
    this.finalizedBlocks = finalizedBlocks;
    this.finalizedStates = finalizedStates;
    this.checkpointStates = checkpointStates;
    this.deletedCheckpointStates = deletedCheckpointStates;
    this.votes = votes;
  }

  public boolean isEmpty() {
    return genesisTime.isEmpty()
        && justifiedCheckpoint.isEmpty()
        && finalizedCheckpoint.isEmpty()
        && bestJustifiedCheckpoint.isEmpty()
        && hotBlocks.isEmpty()
        && deletedHotBlocks.isEmpty()
        && finalizedBlocks.isEmpty()
        && checkpointStates.isEmpty()
        && deletedCheckpointStates.isEmpty()
        && votes.isEmpty();
  }

  public Optional<UnsignedLong> getGenesisTime() {
    return genesisTime;
  }

  public Optional<Checkpoint> getJustifiedCheckpoint() {
    return justifiedCheckpoint;
  }

  public Optional<Checkpoint> getFinalizedCheckpoint() {
    return finalizedCheckpoint;
  }

  public Optional<Checkpoint> getBestJustifiedCheckpoint() {
    return bestJustifiedCheckpoint;
  }

  public Map<Bytes32, SignedBeaconBlock> getHotBlocks() {
    return hotBlocks;
  }

  public Set<Bytes32> getDeletedHotBlocks() {
    return deletedHotBlocks;
  }

  public Set<SignedBeaconBlock> getFinalizedBlocks() {
    return finalizedBlocks;
  }

  public Map<Bytes32, BeaconState> getFinalizedStates() {
    return finalizedStates;
  }

  public Optional<BeaconState> getLatestFinalizedState() {
    return latestFinalizedState;
  }

  public Map<Checkpoint, BeaconState> getCheckpointStates() {
    return checkpointStates;
  }

  public Set<Checkpoint> getDeletedCheckpointStates() {
    return deletedCheckpointStates;
  }

  public Map<UnsignedLong, VoteTracker> getVotes() {
    return votes;
  }
}
