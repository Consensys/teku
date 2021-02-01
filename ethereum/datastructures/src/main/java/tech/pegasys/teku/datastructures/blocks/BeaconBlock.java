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

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.util.SpecDependent;
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
    implements BeaconBlockSummary, SimpleOffsetSerializable, SSZContainer {

  public static class BeaconBlockType
      extends ContainerType5<
          BeaconBlock, UInt64View, UInt64View, Bytes32View, Bytes32View, BeaconBlockBody> {

    public BeaconBlockType() {
      super(
          "BeaconBlock",
          namedType("slot", BasicViewTypes.UINT64_TYPE),
          namedType("proposer_index", BasicViewTypes.UINT64_TYPE),
          namedType("parent_root", BasicViewTypes.BYTES32_TYPE),
          namedType("state_root", BasicViewTypes.BYTES32_TYPE),
          namedType("body", BeaconBlockBody.TYPE.get()));
    }

    @Override
    public BeaconBlock createFromBackingNode(TreeNode node) {
      return new BeaconBlock(this, node);
    }

    public BeaconBlock fromGenesisState(final BeaconState genesisState) {
      return new BeaconBlock(
          this,
          UInt64.ZERO,
          UInt64.ZERO,
          Bytes32.ZERO,
          genesisState.hashTreeRoot(),
          new BeaconBlockBody());
    }
  }

  @SszTypeDescriptor
  public static BeaconBlockType getSszType() {
    return TYPE.get();
  }

  public static final SpecDependent<BeaconBlockType> TYPE = SpecDependent.of(BeaconBlockType::new);

  private BeaconBlock(BeaconBlockType type, TreeNode backingNode) {
    super(type, backingNode);
  }

  @Deprecated
  public BeaconBlock(
      UInt64 slot,
      UInt64 proposer_index,
      Bytes32 parent_root,
      Bytes32 state_root,
      BeaconBlockBody body) {
    this(TYPE.get(), slot, proposer_index, parent_root, state_root, body);
  }

  public BeaconBlock(
      BeaconBlockType type,
      UInt64 slot,
      UInt64 proposer_index,
      Bytes32 parent_root,
      Bytes32 state_root,
      BeaconBlockBody body) {
    super(
        type,
        new UInt64View(slot),
        new UInt64View(proposer_index),
        new Bytes32View(parent_root),
        new Bytes32View(state_root),
        body);
  }

  @Deprecated
  public static BeaconBlock fromGenesisState(final BeaconState genesisState) {
    return new BeaconBlock(
        TYPE.get(),
        UInt64.ZERO,
        UInt64.ZERO,
        Bytes32.ZERO,
        genesisState.hashTreeRoot(),
        new BeaconBlockBody());
  }

  public BeaconBlock withStateRoot(Bytes32 stateRoot) {
    return new BeaconBlock(
        getType(), getSlot(), getProposerIndex(), getParentRoot(), stateRoot, getBody());
  }

  @Override
  public BeaconBlockType getType() {
    return (BeaconBlockType) super.getType();
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
    return getBody().hashTreeRoot();
  }

  @Override
  public Bytes32 getRoot() {
    return hashTreeRoot();
  }

  @Override
  public Optional<BeaconBlock> getBeaconBlock() {
    return Optional.of(this);
  }
}
