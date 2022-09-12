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

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.pow.api.DepositTreeSnapshot;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.SlotAndExecutionPayloadSummary;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;

public class OnDiskStoreData {

  private final UInt64 time;
  private final UInt64 genesisTime;
  private final Optional<Checkpoint> anchor;
  private final AnchorPoint latestFinalized;
  private final Checkpoint justifiedCheckpoint;
  private final Checkpoint bestJustifiedCheckpoint;
  private final Map<Bytes32, StoredBlockMetadata> blockInformation;
  private final Map<UInt64, VoteTracker> votes;
  private final Optional<SlotAndExecutionPayloadSummary> finalizedOptimisticTransitionPayload;
  private final Optional<DepositTreeSnapshot> finalizedDepositSnapshot;

  public OnDiskStoreData(
      final UInt64 time,
      final Optional<Checkpoint> anchor,
      final UInt64 genesisTime,
      final AnchorPoint latestFinalized,
      final Optional<SlotAndExecutionPayloadSummary> finalizedOptimisticTransitionPayload,
      final Optional<DepositTreeSnapshot> finalizedDepositSnapshot,
      final Checkpoint justifiedCheckpoint,
      final Checkpoint bestJustifiedCheckpoint,
      final Map<Bytes32, StoredBlockMetadata> blockInformation,
      final Map<UInt64, VoteTracker> votes) {

    this.time = time;
    this.anchor = anchor;
    this.genesisTime = genesisTime;
    this.latestFinalized = latestFinalized;
    this.finalizedOptimisticTransitionPayload = finalizedOptimisticTransitionPayload;
    this.finalizedDepositSnapshot = finalizedDepositSnapshot;
    this.justifiedCheckpoint = justifiedCheckpoint;
    this.bestJustifiedCheckpoint = bestJustifiedCheckpoint;
    this.blockInformation = blockInformation;
    this.votes = votes;
  }

  public UInt64 getTime() {
    return time;
  }

  public UInt64 getGenesisTime() {
    return genesisTime;
  }

  public Optional<Checkpoint> getAnchor() {
    return anchor;
  }

  public AnchorPoint getLatestFinalized() {
    return latestFinalized;
  }

  public Checkpoint getJustifiedCheckpoint() {
    return justifiedCheckpoint;
  }

  public Checkpoint getBestJustifiedCheckpoint() {
    return bestJustifiedCheckpoint;
  }

  public Map<Bytes32, StoredBlockMetadata> getBlockInformation() {
    return blockInformation;
  }

  public Map<UInt64, VoteTracker> getVotes() {
    return votes;
  }

  public Optional<SlotAndExecutionPayloadSummary> getFinalizedOptimisticTransitionPayload() {
    return finalizedOptimisticTransitionPayload;
  }

  public Optional<DepositTreeSnapshot> getFinalizedDepositSnapshot() {
    return finalizedDepositSnapshot;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final OnDiskStoreData that = (OnDiskStoreData) o;
    return Objects.equals(time, that.time)
        && Objects.equals(genesisTime, that.genesisTime)
        && Objects.equals(anchor, that.anchor)
        && Objects.equals(latestFinalized, that.latestFinalized)
        && Objects.equals(justifiedCheckpoint, that.justifiedCheckpoint)
        && Objects.equals(bestJustifiedCheckpoint, that.bestJustifiedCheckpoint)
        && Objects.equals(blockInformation, that.blockInformation)
        && Objects.equals(votes, that.votes)
        && Objects.equals(
            finalizedOptimisticTransitionPayload, that.finalizedOptimisticTransitionPayload)
        && Objects.equals(finalizedDepositSnapshot, that.finalizedDepositSnapshot);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        time,
        genesisTime,
        anchor,
        latestFinalized,
        justifiedCheckpoint,
        bestJustifiedCheckpoint,
        blockInformation,
        votes,
        finalizedOptimisticTransitionPayload,
        finalizedDepositSnapshot);
  }
}
