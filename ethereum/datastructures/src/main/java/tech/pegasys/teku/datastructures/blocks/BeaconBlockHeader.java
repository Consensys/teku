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

package tech.pegasys.teku.datastructures.blocks;

import com.google.common.base.MoreObjects;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.util.Merkleizable;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.backing.containers.Container5;
import tech.pegasys.teku.ssz.backing.containers.ContainerType5;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.BasicViewTypes;
import tech.pegasys.teku.ssz.backing.view.BasicViews.Bytes32View;
import tech.pegasys.teku.ssz.backing.view.BasicViews.UInt64View;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;
import tech.pegasys.teku.ssz.sos.SszTypeDescriptor;

public class BeaconBlockHeader
    extends Container5<
        BeaconBlockHeader, UInt64View, UInt64View, Bytes32View, Bytes32View, Bytes32View>
    implements Merkleizable, SimpleOffsetSerializable, SSZContainer, BeaconBlockSummary {

  public static class BeaconBlockHeaderType
      extends ContainerType5<
          BeaconBlockHeader, UInt64View, UInt64View, Bytes32View, Bytes32View, Bytes32View> {

    public BeaconBlockHeaderType() {
      super(
          BasicViewTypes.UINT64_TYPE,
          BasicViewTypes.UINT64_TYPE,
          BasicViewTypes.BYTES32_TYPE,
          BasicViewTypes.BYTES32_TYPE,
          BasicViewTypes.BYTES32_TYPE);
    }

    @Override
    public BeaconBlockHeader createFromBackingNode(TreeNode node) {
      return new BeaconBlockHeader(this, node);
    }
  }

  @SszTypeDescriptor public static final BeaconBlockHeaderType TYPE = new BeaconBlockHeaderType();

  private BeaconBlockHeader(BeaconBlockHeaderType type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public BeaconBlockHeader(
      UInt64 slot,
      UInt64 proposer_index,
      Bytes32 parent_root,
      Bytes32 state_root,
      Bytes32 body_root) {
    super(
        TYPE,
        new UInt64View(slot),
        new UInt64View(proposer_index),
        new Bytes32View(parent_root),
        new Bytes32View(state_root),
        new Bytes32View(body_root));
  }

  public BeaconBlockHeader(BeaconBlockHeader header) {
    super(TYPE, header.getBackingNode());
  }

  public BeaconBlockHeader() {
    super(TYPE);
  }

  /**
   * Returns the block header associated with this state
   *
   * @param state A beacon state
   * @return The latest block header from the state, with stateRoot pointing to the supplied state
   */
  public static BeaconBlockHeader fromState(final BeaconState state) {
    BeaconBlockHeader latestHeader = state.getLatest_block_header();

    if (latestHeader.getStateRoot().isZero()) {
      // If the state root is empty, replace it with the current state root
      final Bytes32 stateRoot = state.hash_tree_root();
      latestHeader =
          new BeaconBlockHeader(
              latestHeader.getSlot(),
              latestHeader.getProposerIndex(),
              latestHeader.getParentRoot(),
              stateRoot,
              latestHeader.getBodyRoot());
    }

    return latestHeader;
  }

  @Override
  public UInt64 getSlot() {
    return getField0().get();
  }

  @Override
  public UInt64 getProposerIndex() {
    return getField1().get();
  }

  @Override
  public Bytes32 getParentRoot() {
    return getField2().get();
  }

  @Override
  public Bytes32 getStateRoot() {
    return getField3().get();
  }

  @Override
  public Bytes32 getBodyRoot() {
    return getField4().get();
  }

  @Override
  public Bytes32 getRoot() {
    return hash_tree_root();
  }

  @Override
  public Bytes32 hash_tree_root() {
    return hashTreeRoot();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("slot", getSlot())
        .add("proposer_index", getProposerIndex())
        .add("parent_root", getParentRoot())
        .add("state_root", getStateRoot())
        .add("body_root", getBodyRoot())
        .toString();
  }
}
