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

package tech.pegasys.teku.datastructures.blocks;

import com.google.common.base.MoreObjects;
import com.google.common.base.Suppliers;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import jdk.jfr.Label;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.datastructures.util.HashTreeUtil;
import tech.pegasys.teku.datastructures.util.HashTreeUtil.SSZTypes;
import tech.pegasys.teku.datastructures.util.Merkleizable;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;

public class SignedBeaconBlock
    implements BeaconBlockSummary, Merkleizable, SimpleOffsetSerializable, SSZContainer {

  private final BeaconBlock message;
  private final BLSSignature signature;

  @Label("sos-ignore")
  private final Supplier<Bytes32> hashTreeRoot = Suppliers.memoize(this::calculateRoot);

  public SignedBeaconBlock(final BeaconBlock message, final BLSSignature signature) {
    this.message = message;
    this.signature = signature;
  }

  public BeaconBlock getMessage() {
    return message;
  }

  public BLSSignature getSignature() {
    return signature;
  }

  @Override
  public int getSSZFieldCount() {
    return message.getSSZFieldCount() + signature.getSSZFieldCount();
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    final List<Bytes> parts = new ArrayList<>();
    parts.add(Bytes.EMPTY);
    parts.addAll(signature.get_fixed_parts());
    return parts;
  }

  @Override
  public List<Bytes> get_variable_parts() {
    return List.of(SimpleOffsetSerializer.serialize(message), Bytes.EMPTY);
  }

  @Override
  public UInt64 getSlot() {
    return message.getSlot();
  }

  @Override
  public Bytes32 getParentRoot() {
    return message.getParentRoot();
  }

  @Override
  public UInt64 getProposerIndex() {
    return message.getProposerIndex();
  }

  @Override
  public Bytes32 getBodyRoot() {
    return message.getBodyRoot();
  }

  @Override
  public Optional<BeaconBlock> getBeaconBlock() {
    return Optional.of(message);
  }

  @Override
  public Optional<SignedBeaconBlock> getSignedBeaconBlock() {
    return Optional.of(this);
  }

  /**
   * Get the state root of the BeaconBlock that is being signed.
   *
   * @return The hashed tree root of the {@code BeaconBlock} being signed.
   */
  @Override
  public Bytes32 getStateRoot() {
    return message.getStateRoot();
  }

  /**
   * Get the root of the BeaconBlock that is being signed.
   *
   * @return The hashed tree root of the {@code BeaconBlock} being signed.
   */
  @Override
  public Bytes32 getRoot() {
    return message.hash_tree_root();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SignedBeaconBlock that = (SignedBeaconBlock) o;
    return Objects.equals(message, that.message) && Objects.equals(signature, that.signature);
  }

  @Override
  public int hashCode() {
    return Objects.hash(message, signature);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("message", message)
        .add("signature", signature)
        .toString();
  }

  @Override
  public Bytes32 hash_tree_root() {
    return hashTreeRoot.get();
  }

  public Bytes32 calculateRoot() {
    return HashTreeUtil.merkleize(
        List.of(
            message.hash_tree_root(),
            HashTreeUtil.hash_tree_root(SSZTypes.VECTOR_OF_BASIC, signature.toSSZBytes())));
  }
}
