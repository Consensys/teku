/*
 * Copyright 2020 ConsenSys AG.
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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;

public class FinalizedChainData {
  private final Checkpoint finalizedCheckpoint;
  private final BeaconState latestFinalizedState;
  private final Map<Bytes32, SignedBeaconBlock> finalizedBlocks;
  private final Map<Bytes32, BeaconState> finalizedStates;

  private FinalizedChainData(
      final Checkpoint finalizedCheckpoint,
      final BeaconState latestFinalizedState,
      final Map<Bytes32, SignedBeaconBlock> finalizedBlocks,
      final Map<Bytes32, BeaconState> finalizedStates) {
    this.finalizedCheckpoint = finalizedCheckpoint;
    this.latestFinalizedState = latestFinalizedState;
    this.finalizedBlocks = finalizedBlocks;
    this.finalizedStates = finalizedStates;
  }

  public static Builder builder() {
    return new Builder();
  }

  public Checkpoint getFinalizedCheckpoint() {
    return finalizedCheckpoint;
  }

  public BeaconState getLatestFinalizedState() {
    return latestFinalizedState;
  }

  public Map<Bytes32, SignedBeaconBlock> getBlocks() {
    return finalizedBlocks;
  }

  public Map<Bytes32, BeaconState> getStates() {
    return finalizedStates;
  }

  public static class Builder {
    private Checkpoint finalizedCheckpoint;
    private BeaconState latestFinalizedState;
    private Map<Bytes32, SignedBeaconBlock> finalizedBlocks = new HashMap<>();
    private Map<Bytes32, BeaconState> finalizedStates = new HashMap<>();

    public FinalizedChainData build() {
      assertValid();
      return new FinalizedChainData(
          finalizedCheckpoint, latestFinalizedState, finalizedBlocks, finalizedStates);
    }

    private void assertValid() {
      checkState(finalizedCheckpoint != null, "Finalized checkpoint must be set");
      checkState(latestFinalizedState != null, "Latest finalized state must be set");
    }

    public Builder finalizedCheckpoint(final Checkpoint finalizedCheckpoint) {
      checkNotNull(finalizedCheckpoint);
      this.finalizedCheckpoint = finalizedCheckpoint;
      return this;
    }

    public Builder latestFinalizedState(final BeaconState latestFinalizedState) {
      checkNotNull(latestFinalizedState);
      this.latestFinalizedState = latestFinalizedState;
      return this;
    }

    public Builder finalizedBlocks(final Collection<SignedBeaconBlock> finalizedBlocks) {
      checkNotNull(finalizedBlocks);
      finalizedBlocks.forEach(this::finalizedBlock);
      return this;
    }

    public Builder finalizedStates(final Map<Bytes32, BeaconState> finalizedStates) {
      checkNotNull(finalizedStates);
      this.finalizedStates.putAll(finalizedStates);
      return this;
    }

    public Builder finalizedBlock(final SignedBeaconBlock block) {
      checkNotNull(block);
      this.finalizedBlocks.put(block.getRoot(), block);
      return this;
    }

    public Builder finalizedState(final Bytes32 blockRoot, final BeaconState finalizedState) {
      checkNotNull(finalizedStates);
      checkNotNull(blockRoot);
      this.finalizedStates.put(blockRoot, finalizedState);
      return this;
    }
  }
}
