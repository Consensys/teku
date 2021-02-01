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

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ssz.SSZTypes.SSZBackingVector;
import tech.pegasys.teku.ssz.SSZTypes.SSZVector;
import tech.pegasys.teku.ssz.backing.VectorViewRead;
import tech.pegasys.teku.ssz.backing.containers.Container2;
import tech.pegasys.teku.ssz.backing.containers.ContainerType2;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.BasicViewTypes;
import tech.pegasys.teku.ssz.backing.type.VectorViewType;
import tech.pegasys.teku.ssz.backing.view.AbstractBasicView;
import tech.pegasys.teku.ssz.backing.view.BasicViews.Bytes32View;
import tech.pegasys.teku.ssz.backing.view.ViewUtils;
import tech.pegasys.teku.ssz.sos.SszTypeDescriptor;
import tech.pegasys.teku.util.config.Constants;

public class Deposit extends Container2<Deposit, VectorViewRead<Bytes32View>, DepositData> {

  public static class DepositType
      extends ContainerType2<Deposit, VectorViewRead<Bytes32View>, DepositData> {

    public DepositType() {
      super(
          "Deposit",
          namedType(
              "proof",
              new VectorViewType<>(
                  BasicViewTypes.BYTES32_TYPE, Constants.DEPOSIT_CONTRACT_TREE_DEPTH + 1)),
          namedType("data", DepositData.TYPE));
    }

    public VectorViewType<Bytes32View> getProofType() {
      return (VectorViewType<Bytes32View>) getFieldType0();
    }

    @Override
    public Deposit createFromBackingNode(TreeNode node) {
      return new Deposit(this, node);
    }
  }

  @SszTypeDescriptor public static final DepositType TYPE = new DepositType();

  private static final SSZVector<Bytes32> EMPTY_PROOF =
      SSZVector.createMutable(TYPE.getProofType().getLength(), Bytes32.ZERO);

  private Deposit(DepositType type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public Deposit(SSZVector<Bytes32> proof, DepositData data) {
    super(TYPE, ViewUtils.toVectorView(TYPE.getProofType(), proof, Bytes32View::new), data);
  }

  public Deposit() {
    super(TYPE);
  }

  public Deposit(DepositData data) {
    this(EMPTY_PROOF, data);
  }

  public SSZVector<Bytes32> getProof() {
    return new SSZBackingVector<>(
        Bytes32.class, getField0(), Bytes32View::new, AbstractBasicView::get);
  }

  public DepositData getData() {
    return getField1();
  }
}
