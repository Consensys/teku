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

package tech.pegasys.teku.datastructures.operations;

import com.google.common.base.MoreObjects;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.datastructures.util.Merkleizable;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.backing.VectorViewRead;
import tech.pegasys.teku.ssz.backing.containers.Container2;
import tech.pegasys.teku.ssz.backing.containers.ContainerType2;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.BasicViewTypes;
import tech.pegasys.teku.ssz.backing.type.VectorViewType;
import tech.pegasys.teku.ssz.backing.view.BasicViews.ByteView;
import tech.pegasys.teku.ssz.backing.view.ViewUtils;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;
import tech.pegasys.teku.ssz.sos.SszTypeDescriptor;

public class SignedAggregateAndProof
    extends Container2<SignedAggregateAndProof, AggregateAndProof, VectorViewRead<ByteView>>
    implements SimpleOffsetSerializable, SSZContainer, Merkleizable {

  @SszTypeDescriptor
  public static final ContainerType2<
          SignedAggregateAndProof, AggregateAndProof, VectorViewRead<ByteView>>
      TYPE =
          ContainerType2.create(
              AggregateAndProof.TYPE,
              new VectorViewType<>(BasicViewTypes.BYTE_TYPE, 96),
              SignedAggregateAndProof::new);

  private AggregateAndProof message;
  private BLSSignature signature;

  public SignedAggregateAndProof(
      ContainerType2<SignedAggregateAndProof, AggregateAndProof, VectorViewRead<ByteView>> type,
      TreeNode backingNode) {
    super(type, backingNode);
  }

  public SignedAggregateAndProof(final AggregateAndProof message, final BLSSignature signature) {
    super(TYPE, message, ViewUtils.createVectorFromBytes(signature.toBytesCompressed()));
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

  public AggregateAndProof getMessage() {
    return getField0();
  }

  public BLSSignature getSignature() {
    return BLSSignature.fromBytesCompressed(ViewUtils.getAllBytes(getField1()));
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
    return hashTreeRoot();
  }
}
