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
import com.google.common.base.Suppliers;
import java.util.List;
import java.util.function.Supplier;
import jdk.jfr.Label;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.util.Merkleizable;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.BasicViewTypes;
import tech.pegasys.teku.ssz.backing.type.ContainerViewType;
import tech.pegasys.teku.ssz.backing.view.AbstractImmutableContainer;
import tech.pegasys.teku.ssz.backing.view.BasicViews.Bytes32View;
import tech.pegasys.teku.ssz.backing.view.BasicViews.UInt64View;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;

public class BeaconBlockHeader extends AbstractImmutableContainer
    implements Merkleizable, SimpleOffsetSerializable, SSZContainer {

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  public static final int SSZ_FIELD_COUNT = 4;

  public static final ContainerViewType<BeaconBlockHeader> TYPE =
      new ContainerViewType<>(
          List.of(
              BasicViewTypes.UINT64_TYPE,
              BasicViewTypes.UINT64_TYPE,
              BasicViewTypes.BYTES32_TYPE,
              BasicViewTypes.BYTES32_TYPE,
              BasicViewTypes.BYTES32_TYPE),
          BeaconBlockHeader::new);

  @SuppressWarnings("unused")
  private final UInt64 slot = null;

  @SuppressWarnings("unused")
  private final UInt64 proposer_index = null;

  @SuppressWarnings("unused")
  private final Bytes32 parent_root = null;

  @SuppressWarnings("unused")
  private final Bytes32 state_root = null;

  @SuppressWarnings("unused")
  private final Bytes32 body_root = null;

  @Label("sos-ignore")
  private final Supplier<Bytes32> hashTreeRootSupplier = Suppliers.memoize(this::calculateRoot);

  private BeaconBlockHeader(ContainerViewType<BeaconBlockHeader> type, TreeNode backingNode) {
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

    if (latestHeader.getState_root().isZero()) {
      // If the state root is empty, replace it with the current state root
      final Bytes32 stateRoot = state.hash_tree_root();
      latestHeader =
          new BeaconBlockHeader(
              latestHeader.getSlot(),
              latestHeader.getProposer_index(),
              latestHeader.getParent_root(),
              stateRoot,
              latestHeader.getBody_root());
    }

    return latestHeader;
  }

  public static BeaconBlockHeader fromBlock(final BeaconBlock block) {
    return new BeaconBlockHeader(
        block.getSlot(),
        block.getProposer_index(),
        block.getParent_root(),
        block.getState_root(),
        block.getBody().hash_tree_root());
  }

  public static BeaconBlockHeader fromBlock(final SignedBeaconBlock block) {
    return fromBlock(block.getMessage());
  }

  @Override
  public int getSSZFieldCount() {
    return SSZ_FIELD_COUNT;
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    return List.of(
        SSZ.encodeUInt64(getSlot().longValue()),
        SSZ.encodeUInt64(getProposer_index().longValue()),
        SSZ.encode(writer -> writer.writeFixedBytes(getParent_root())),
        SSZ.encode(writer -> writer.writeFixedBytes(getState_root())),
        SSZ.encode(writer -> writer.writeFixedBytes(getBody_root())));
  }

  /** *************** * GETTERS & SETTERS * * ******************* */
  public UInt64 getSlot() {
    return ((UInt64View) get(0)).get();
  }

  public UInt64 getProposer_index() {
    return ((UInt64View) get(1)).get();
  }

  public Bytes32 getParent_root() {
    return ((Bytes32View) get(2)).get();
  }

  public Bytes32 getState_root() {
    return ((Bytes32View) get(3)).get();
  }

  public Bytes32 getBody_root() {
    return ((Bytes32View) get(4)).get();
  }

  @Override
  public Bytes32 hash_tree_root() {
    return hashTreeRootSupplier.get();
  }

  public Bytes32 calculateRoot() {
    return hashTreeRoot();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("slot", getSlot())
        .add("proposer_index", getProposer_index())
        .add("parent_root", getParent_root())
        .add("state_root", getState_root())
        .add("body_root", getBody_root())
        .toString();
  }
}
