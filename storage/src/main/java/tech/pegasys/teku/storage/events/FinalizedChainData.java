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
import tech.pegasys.teku.datastructures.state.AnchorPoint;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;

public class FinalizedChainData {
  private final AnchorPoint latestFinalized;
  private final Map<Bytes32, Bytes32> finalizedChildToParentMap;
  private final Map<Bytes32, SignedBeaconBlock> finalizedBlocks;
  private final Map<Bytes32, BeaconState> finalizedStates;

  private FinalizedChainData(
      final AnchorPoint latestFinalized,
      final Map<Bytes32, Bytes32> finalizedChildToParentMap,
      final Map<Bytes32, SignedBeaconBlock> finalizedBlocks,
      final Map<Bytes32, BeaconState> finalizedStates) {
    this.latestFinalized = latestFinalized;
    this.finalizedChildToParentMap = finalizedChildToParentMap;
    this.finalizedBlocks = finalizedBlocks;
    this.finalizedStates = finalizedStates;
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
    private AnchorPoint latestFinalized;
    private final Map<Bytes32, Bytes32> finalizedChildToParentMap = new HashMap<>();
    private Map<Bytes32, SignedBeaconBlock> finalizedBlocks = new HashMap<>();
    private Map<Bytes32, BeaconState> finalizedStates = new HashMap<>();

    public FinalizedChainData build() {
      assertValid();
      return new FinalizedChainData(
          latestFinalized, finalizedChildToParentMap, finalizedBlocks, finalizedStates);
    }

    private void assertValid() {
      checkState(latestFinalized != null, "Latest finalized data must be set");
      checkState(!finalizedChildToParentMap.isEmpty(), "Must supply finalized roots");
      checkState(
          finalizedChildToParentMap.containsKey(latestFinalized.getRoot()),
          "Must supply finalized parent");
    }

    public Builder latestFinalized(final AnchorPoint latestFinalized) {
      checkNotNull(latestFinalized);
      this.latestFinalized = latestFinalized;
      this.finalizedBlocks.put(latestFinalized.getRoot(), latestFinalized.getBlock());
      this.finalizedStates.put(latestFinalized.getRoot(), latestFinalized.getState());
      finalizedChildAndParent(latestFinalized.getRoot(), latestFinalized.getParentRoot());
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
