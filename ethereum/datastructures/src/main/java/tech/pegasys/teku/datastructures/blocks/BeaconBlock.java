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
import java.util.Optional;
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

public final class BeaconBlock
    extends Container5<
        BeaconBlock, UInt64View, UInt64View, Bytes32View, Bytes32View, BeaconBlockBody>
    implements BeaconBlockSummary, Merkleizable, SimpleOffsetSerializable, SSZContainer {

  public static class BeaconBlockType
      extends ContainerType5<
          BeaconBlock, UInt64View, UInt64View, Bytes32View, Bytes32View, BeaconBlockBody> {

    public BeaconBlockType() {
      super(
          BasicViewTypes.UINT64_TYPE,
          BasicViewTypes.UINT64_TYPE,
          BasicViewTypes.BYTES32_TYPE,
          BasicViewTypes.BYTES32_TYPE,
          BeaconBlockBody.TYPE.get());
    }

    @Override
    public BeaconBlock createFromBackingNode(TreeNode node) {
      return new BeaconBlock(this, node);
    }
  }

  @SszTypeDescriptor public static final BeaconBlockType TYPE = new BeaconBlockType();

  private BeaconBlock(BeaconBlockType type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public BeaconBlock(
      UInt64 slot,
      UInt64 proposer_index,
      Bytes32 parent_root,
      Bytes32 state_root,
      BeaconBlockBody body) {
    super(
        TYPE,
        new UInt64View(slot),
        new UInt64View(proposer_index),
        new Bytes32View(parent_root),
        new Bytes32View(state_root),
        body);
  }

  public static BeaconBlock fromGenesisState(final BeaconState genesisState) {
    return new BeaconBlock(
        UInt64.ZERO, UInt64.ZERO, Bytes32.ZERO, genesisState.hashTreeRoot(), new BeaconBlockBody());
  }

  public BeaconBlock withStateRoot(Bytes32 stateRoot) {
    return new BeaconBlock(getSlot(), getProposerIndex(), getParentRoot(), stateRoot, getBody());
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

  public BeaconBlockBody getBody() {
    return getField4();
  }

  @Override
  public Bytes32 getBodyRoot() {
    return getBody().hash_tree_root();
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
  public Optional<BeaconBlock> getBeaconBlock() {
    return Optional.of(this);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("root", hash_tree_root())
        .add("slot", getSlot())
        .add("proposer_index", getProposerIndex())
        .add("parent_root", getParentRoot())
        .add("state_root", getStateRoot())
        .add("body", getBody().hash_tree_root())
        .toString();
  }
}
