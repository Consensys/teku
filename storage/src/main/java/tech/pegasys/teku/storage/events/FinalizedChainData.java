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
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.state.AnchorPoint;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;

public class FinalizedChainData {
  private final AnchorPoint latestFinalized;
  private final Map<Bytes32, Bytes32> finalizedChildToParentMap;
  private final Map<Bytes32, SignedBeaconBlock> finalizedBlocks;
  private final Map<Bytes32, BeaconState> finalizedStates;

  private FinalizedChainData(
      final Checkpoint latestFinalizedCheckpoint,
      final SignedBlockAndState latestFinalizedBlockAndState,
      final Map<Bytes32, Bytes32> finalizedChildToParentMap,
      final Map<Bytes32, SignedBeaconBlock> finalizedBlocks,
      final Map<Bytes32, BeaconState> finalizedStates) {
    this.finalizedChildToParentMap = finalizedChildToParentMap;
    this.finalizedBlocks = finalizedBlocks;
    this.finalizedStates = finalizedStates;

    latestFinalized = AnchorPoint.create(latestFinalizedCheckpoint, latestFinalizedBlockAndState);
  }

  public static Builder builder() {
    return new Builder();
  }

  public Checkpoint getFinalizedCheckpoint() {
    return latestFinalized.getCheckpoint();
  }

  public BeaconState getLatestFinalizedState() {
    return latestFinalized.getState();
  }

  public Map<Bytes32, Bytes32> getFinalizedChildToParentMap() {
    return finalizedChildToParentMap;
  }

  public Map<Bytes32, SignedBeaconBlock> getBlocks() {
    return finalizedBlocks;
  }

  public Map<Bytes32, BeaconState> getStates() {
    return finalizedStates;
  }

  public AnchorPoint getLatestFinalized() {
    return latestFinalized;
  }

  public static class Builder {
    private Checkpoint finalizedCheckpoint;
    private SignedBlockAndState latestFinalizedBlockAndState;
    private final Map<Bytes32, Bytes32> finalizedChildToParentMap = new HashMap<>();
    private Map<Bytes32, SignedBeaconBlock> finalizedBlocks = new HashMap<>();
    private Map<Bytes32, BeaconState> finalizedStates = new HashMap<>();

    public FinalizedChainData build() {
      assertValid();
      return new FinalizedChainData(
          finalizedCheckpoint,
          latestFinalizedBlockAndState,
          finalizedChildToParentMap,
          finalizedBlocks,
          finalizedStates);
    }

    private void assertValid() {
      checkState(finalizedCheckpoint != null, "Finalized checkpoint must be set");
      checkState(
          latestFinalizedBlockAndState != null, "Latest finalized block and state must be set");
      checkState(
          finalizedCheckpoint.getRoot().equals(latestFinalizedBlockAndState.getRoot()),
          "Latest finalized block and state must match finalized checkpoint");
      checkState(!finalizedChildToParentMap.isEmpty(), "Must supply finalized roots");
      checkState(
          finalizedChildToParentMap.containsKey(finalizedCheckpoint.getRoot()),
          "Must supply finalized parent");
    }

    public Builder finalizedCheckpoint(final Checkpoint finalizedCheckpoint) {
      checkNotNull(finalizedCheckpoint);
      this.finalizedCheckpoint = finalizedCheckpoint;
      return this;
    }

    public Builder latestFinalizedBlockAndState(
        final SignedBlockAndState latestFinalizedBlockAndState) {
      checkNotNull(latestFinalizedBlockAndState);
      this.latestFinalizedBlockAndState = latestFinalizedBlockAndState;
      this.finalizedBlocks.put(
          latestFinalizedBlockAndState.getRoot(), latestFinalizedBlockAndState.getBlock());
      this.finalizedStates.put(
          latestFinalizedBlockAndState.getRoot(), latestFinalizedBlockAndState.getState());
      finalizedChildAndParent(
          latestFinalizedBlockAndState.getRoot(), latestFinalizedBlockAndState.getParentRoot());
      return this;
    }

    public Builder finalizedBlocks(final Collection<SignedBeaconBlock> finalizedBlocks) {
      checkNotNull(finalizedBlocks);
      finalizedBlocks.forEach(this::finalizedBlock);
      return this;
    }

    public Builder finalizedStates(final Map<Bytes32, BeaconState> finalizedStates) {
      checkNotNull(finalizedStates);
      finalizedStates.forEach(this::finalizedState);
      return this;
    }

    public Builder finalizedBlock(final SignedBeaconBlock block) {
      checkNotNull(block);
      this.finalizedBlocks.put(block.getRoot(), block);
      this.finalizedChildAndParent(block.getRoot(), block.getParent_root());
      return this;
    }

    public Builder finalizedState(final Bytes32 blockRoot, final BeaconState finalizedState) {
      checkNotNull(finalizedStates);
      checkNotNull(blockRoot);
      this.finalizedStates.put(blockRoot, finalizedState);
      return this;
    }

    public Builder finalizedChildAndParent(final Map<Bytes32, Bytes32> childToParentMap) {
      checkNotNull(childToParentMap);
      this.finalizedChildToParentMap.putAll(childToParentMap);
      return this;
    }

    public Builder finalizedChildAndParent(final Bytes32 child, final Bytes32 parent) {
      checkNotNull(child);
      checkNotNull(parent);
      this.finalizedChildToParentMap.put(child, parent);
      return this;
    }
  }
}
