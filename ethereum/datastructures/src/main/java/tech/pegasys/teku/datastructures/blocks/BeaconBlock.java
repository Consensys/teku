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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import jdk.jfr.Label;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.util.HashTreeUtil;
import tech.pegasys.teku.datastructures.util.HashTreeUtil.SSZTypes;
import tech.pegasys.teku.datastructures.util.Merkleizable;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;

public final class BeaconBlock
    implements BeaconBlockSummary, Merkleizable, SimpleOffsetSerializable, SSZContainer {

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  public static final int SSZ_FIELD_COUNT = 4;

  // Header
  private final UInt64 slot;
  private final UInt64 proposer_index;
  private final Bytes32 parent_root;
  private final Bytes32 state_root;

  // Body
  private final BeaconBlockBody body;

  @Label("sos-ignore")
  private final Supplier<Bytes32> hashTreeRoot = Suppliers.memoize(this::calculateRoot);

  public BeaconBlock(
      UInt64 slot,
      UInt64 proposer_index,
      Bytes32 parent_root,
      Bytes32 state_root,
      BeaconBlockBody body) {
    this.slot = slot;
    this.proposer_index = proposer_index;
    this.parent_root = parent_root;
    this.state_root = state_root;
    this.body = body;
  }

  public BeaconBlock(BeaconBlock block, Bytes32 stateRoot) {
    this.slot = block.getSlot();
    this.proposer_index = block.getProposerIndex();
    this.parent_root = block.getParentRoot();
    this.body = block.getBody();
    this.state_root = stateRoot;
  }

  public BeaconBlock() {
    this.slot = UInt64.ZERO;
    this.proposer_index = UInt64.ZERO;
    this.parent_root = Bytes32.ZERO;
    this.state_root = Bytes32.ZERO;
    this.body = new BeaconBlockBody();
  }

  public BeaconBlock(Bytes32 state_root) {
    this.slot = UInt64.ZERO;
    this.proposer_index = UInt64.ZERO;
    this.parent_root = Bytes32.ZERO;
    this.state_root = state_root;
    this.body = new BeaconBlockBody();
  }

  public static BeaconBlock fromGenesisState(final BeaconState genesisState) {
    return new BeaconBlock(genesisState.hash_tree_root());
  }

  @Override
  public int getSSZFieldCount() {
    return SSZ_FIELD_COUNT + body.getSSZFieldCount();
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    return List.of(
        SSZ.encodeUInt64(slot.longValue()),
        SSZ.encodeUInt64(proposer_index.longValue()),
        SSZ.encode(writer -> writer.writeFixedBytes(parent_root)),
        SSZ.encode(writer -> writer.writeFixedBytes(state_root)),
        Bytes.EMPTY);
  }

  @Override
  public List<Bytes> get_variable_parts() {
    return List.of(
        Bytes.EMPTY, Bytes.EMPTY, Bytes.EMPTY, Bytes.EMPTY, SimpleOffsetSerializer.serialize(body));
  }

  @Override
  public int hashCode() {
    return Objects.hash(slot, proposer_index, parent_root, state_root, body);
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof BeaconBlock)) {
      return false;
    }

    BeaconBlock other = (BeaconBlock) obj;
    return Objects.equals(this.getSlot(), other.getSlot())
        && Objects.equals(this.getProposerIndex(), other.getProposerIndex())
        && Objects.equals(this.getParentRoot(), other.getParentRoot())
        && Objects.equals(this.getStateRoot(), other.getStateRoot())
        && Objects.equals(this.getBody(), other.getBody());
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public BeaconBlockBody getBody() {
    return body;
  }

  @Override
  public Bytes32 getBodyRoot() {
    return body.hash_tree_root();
  }

  @Override
  public Bytes32 getStateRoot() {
    return state_root;
  }

  @Override
  public Bytes32 getParentRoot() {
    return parent_root;
  }

  @Override
  public UInt64 getSlot() {
    return slot;
  }

  @Override
  public UInt64 getProposerIndex() {
    return proposer_index;
  }

  @Override
  public Bytes32 getRoot() {
    return hash_tree_root();
  }

  @Override
  public Bytes32 hash_tree_root() {
    return hashTreeRoot.get();
  }

  @Override
  public Optional<BeaconBlock> getBeaconBlock() {
    return Optional.of(this);
  }

  public Bytes32 calculateRoot() {
    return HashTreeUtil.merkleize(
        Arrays.asList(
            HashTreeUtil.hash_tree_root(SSZTypes.BASIC, SSZ.encodeUInt64(slot.longValue())),
            HashTreeUtil.hash_tree_root(
                SSZTypes.BASIC, SSZ.encodeUInt64(proposer_index.longValue())),
            HashTreeUtil.hash_tree_root(SSZTypes.VECTOR_OF_BASIC, parent_root),
            HashTreeUtil.hash_tree_root(SSZTypes.VECTOR_OF_BASIC, state_root),
            body.hash_tree_root()));
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("root", hash_tree_root())
        .add("slot", slot)
        .add("proposer_index", proposer_index)
        .add("parent_root", parent_root)
        .add("state_root", state_root)
        .add("body", body.hash_tree_root())
        .toString();
  }
}
