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

package tech.pegasys.teku.protoarray;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;
import tech.pegasys.teku.datastructures.state.AnchorPoint;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.util.config.Constants;

public class ProtoArrayBuilder {

  private Optional<Checkpoint> anchor = Optional.empty();
  private Checkpoint justifiedCheckpoint;
  private Checkpoint finalizedCheckpoint;
  private Optional<ProtoArraySnapshot> protoArraySnapshot = Optional.empty();
  //  private Stream<SlotAndBlockRoot> availableBlocks;
  //  private Function<Bytes32, Optional<SignedBlockAndState>> blockAndStateProvider;

  public static ProtoArray fromAnchorPoint(final AnchorPoint anchor) {
    final ProtoArray protoArray =
        new ProtoArrayBuilder()
            .justifiedCheckpoint(anchor.getCheckpoint())
            .finalizedCheckpoint(anchor.getCheckpoint())
            .build();
    protoArray.onBlock(
        anchor.getBlock().getSlot(),
        anchor.getRoot(),
        anchor.getParentRoot(),
        anchor.getBlock().getStateRoot(),
        anchor.getEpoch(),
        anchor.getEpoch());
    return protoArray;
  }

  public ProtoArrayBuilder anchor(final Optional<Checkpoint> anchor) {
    this.anchor = anchor;
    return this;
  }

  public ProtoArrayBuilder justifiedCheckpoint(final Checkpoint justifiedCheckpoint) {
    this.justifiedCheckpoint = justifiedCheckpoint;
    return this;
  }

  public ProtoArrayBuilder finalizedCheckpoint(final Checkpoint finalizedCheckpoint) {
    this.finalizedCheckpoint = finalizedCheckpoint;
    return this;
  }

  public ProtoArrayBuilder protoArraySnapshot(
      final Optional<ProtoArraySnapshot> protoArraySnapshot) {
    this.protoArraySnapshot = protoArraySnapshot;
    return this;
  }

  //  public ProtoArrayBuilder availableBlocks(final Stream<SlotAndBlockRoot> availableBlocks) {
  //    this.availableBlocks = availableBlocks;
  //    return this;
  //  }
  //
  //  public ProtoArrayBuilder blockAndStateProvider(
  //      final Function<Bytes32, Optional<SignedBlockAndState>> blockAndStateProvider) {
  //    this.blockAndStateProvider = blockAndStateProvider;
  //    return this;
  //  }

  public ProtoArray build() {
    checkNotNull(justifiedCheckpoint, "Justified checkpoint must be supplied");
    checkNotNull(finalizedCheckpoint, "Finalized checkpoint must be supplied");
    //    checkNotNull(availableBlocks, "Available blocks must be supplied");
    //    checkNotNull(blockAndStateProvider, "Block and state provider must be supplied");

    // If no anchor is explicitly set, default to zero (genesis epoch)
    final UInt64 anchorEpoch =
        anchor.map(Checkpoint::getEpoch).orElse(UInt64.valueOf(Constants.GENESIS_EPOCH));
    ProtoArray protoArray =
        protoArraySnapshot
            .map(ProtoArraySnapshot::toProtoArray)
            .orElse(
                new ProtoArray(
                    Constants.PROTOARRAY_FORKCHOICE_PRUNE_THRESHOLD,
                    justifiedCheckpoint.getEpoch(),
                    finalizedCheckpoint.getEpoch(),
                    anchorEpoch,
                    new ArrayList<>(),
                    new HashMap<>()));

    //    processBlocksInStoreAtStartup(protoArray);
    return protoArray;
  }

  //  private void processBlocksInStoreAtStartup(final ProtoArray protoArray) {
  //    final List<Bytes32> sortedMissingBlockRoots =
  //        availableBlocks
  //            .filter(block -> !protoArray.getIndices().containsKey(block.getBlockRoot()))
  //            .sorted(Comparator.comparing(SlotAndBlockRoot::getSlot))
  //            .map(SlotAndBlockRoot::getBlockRoot)
  //            .collect(Collectors.toList());
  //
  //    for (Bytes32 blockRoot : sortedMissingBlockRoots) {
  //      final SignedBlockAndState blockAndState =
  //          blockAndStateProvider.apply(blockRoot).orElseThrow();
  //      processBlockAtStartup(protoArray, blockAndState);
  //    }
  //  }
  //
  //  private void processBlockAtStartup(
  //      final ProtoArray protoArray, final SignedBlockAndState blockAndState) {
  //    final BeaconState state = blockAndState.getState();
  //    protoArray.onBlock(
  //        blockAndState.getSlot(),
  //        blockAndState.getRoot(),
  //        blockAndState.getParentRoot(),
  //        blockAndState.getStateRoot(),
  //        state.getCurrent_justified_checkpoint().getEpoch(),
  //        state.getFinalized_checkpoint().getEpoch());
  //  }
}
