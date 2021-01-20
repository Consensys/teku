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

package tech.pegasys.teku.datastructures.operations;

import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.teku.datastructures.util.Merkleizable;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.SSZBackingList;
import tech.pegasys.teku.ssz.SSZTypes.SSZBackingVector;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.SSZTypes.SSZVector;
import tech.pegasys.teku.ssz.backing.VectorViewRead;
import tech.pegasys.teku.ssz.backing.containers.Container2;
import tech.pegasys.teku.ssz.backing.containers.ContainerType2;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.BasicViewTypes;
import tech.pegasys.teku.ssz.backing.type.VectorViewType;
import tech.pegasys.teku.ssz.backing.view.AbstractBasicView;
import tech.pegasys.teku.ssz.backing.view.BasicViews.Bytes32View;
import tech.pegasys.teku.ssz.backing.view.BasicViews.UInt64View;
import tech.pegasys.teku.ssz.backing.view.ViewUtils;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;
import tech.pegasys.teku.ssz.sos.SszTypeDescriptor;
import tech.pegasys.teku.util.config.Constants;

public class Deposit extends Container2<Deposit, VectorViewRead<Bytes32View>, DepositData>
    implements Merkleizable, SimpleOffsetSerializable, SSZContainer {


  private static final VectorViewType<Bytes32View> PROOF_TYPE = new VectorViewType<>(
      BasicViewTypes.BYTE_TYPE, Constants.DEPOSIT_CONTRACT_TREE_DEPTH + 1);
  private static final SSZVector<Bytes32> EMPTY_PROOF = SSZVector
      .createMutable(PROOF_TYPE.getLength(), Bytes32.ZERO);

  @SszTypeDescriptor
  public static final ContainerType2<Deposit, VectorViewRead<Bytes32View>, DepositData> TYPE =
      ContainerType2.create(
          PROOF_TYPE,
          DepositData.TYPE,
          Deposit::new);

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  public static final int SSZ_FIELD_COUNT = 1;

  private SSZVector<Bytes32> proof; // Vector bounded by DEPOSIT_CONTRACT_TREE_DEPTH + 1
  private DepositData data;

  public Deposit(
      ContainerType2<Deposit, VectorViewRead<Bytes32View>, DepositData> type,
      TreeNode backingNode) {
    super(type, backingNode);
  }

  public Deposit(SSZVector<Bytes32> proof, DepositData data) {
    super(TYPE, ViewUtils.toVectorView(PROOF_TYPE, proof, Bytes32View::new), data);
  }

  public Deposit() {
    super(TYPE);
  }

  public Deposit(DepositData data) {
    this(EMPTY_PROOF, data);
  }

  @Override
  public int getSSZFieldCount() {
    return getData().getSSZFieldCount() + SSZ_FIELD_COUNT;
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    List<Bytes> fixedPartsList = new ArrayList<>();
    fixedPartsList.addAll(
        List.of(SSZ.encode(writer -> writer.writeFixedBytesVector(getProof().asList()))));
    fixedPartsList.addAll(getData().get_fixed_parts());
    return fixedPartsList;
  }

  public SSZVector<Bytes32> getProof() {
    return new SSZBackingVector<>(
        Bytes32.class,
        getField0(),
        Bytes32View::new,
        AbstractBasicView::get);
  }

  public DepositData getData() {
    return getField1();
  }

  @Override
  public Bytes32 hash_tree_root() {
    return hashTreeRoot();
  }
}
