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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.util.BeaconStateUtil;

public class GenesisEvent {
  private final Checkpoint checkpoint;
  private final SignedBeaconBlock block;
  private final BeaconState state;

  private GenesisEvent(Checkpoint checkpoint, SignedBeaconBlock block, BeaconState state) {
    checkArgument(
        block.getStateRoot().equals(state.hash_tree_root()), "Block and state must match");
    checkArgument(checkpoint.getRoot().equals(block.getRoot()), "Checkpoint and block must match");

    this.checkpoint = checkpoint;
    this.block = block;
    this.state = state;
  }

  public static GenesisEvent fromGenesisState(final BeaconState genesisState) {
    final BeaconBlock anchorBlock = new BeaconBlock(genesisState.hash_tree_root());
    final SignedBeaconBlock signedAnchorBlock =
        new SignedBeaconBlock(anchorBlock, BLSSignature.empty());

    final Bytes32 anchorRoot = anchorBlock.hash_tree_root();
    final UnsignedLong anchorEpoch = BeaconStateUtil.get_current_epoch(genesisState);
    final Checkpoint anchorCheckpoint = new Checkpoint(anchorEpoch, anchorRoot);

    return new GenesisEvent(anchorCheckpoint, signedAnchorBlock, genesisState);
  }

  public Checkpoint getCheckpoint() {
    return checkpoint;
  }

  public SignedBeaconBlock getBlock() {
    return block;
  }

  public BeaconState getState() {
    return state;
  }

  public Bytes32 getRoot() {
    return block.getRoot();
  }

  public Bytes32 getParentRoot() {
    return block.getParent_root();
  }

  public SignedBlockAndState toSignedBlockAndState() {
    return new SignedBlockAndState(block, state);
  }
}
