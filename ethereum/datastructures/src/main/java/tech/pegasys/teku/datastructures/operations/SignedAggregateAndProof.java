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

import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ssz.backing.SszVector;
import tech.pegasys.teku.ssz.backing.containers.Container2;
import tech.pegasys.teku.ssz.backing.containers.ContainerType2;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.ComplexViewTypes;
import tech.pegasys.teku.ssz.backing.view.BasicViews.ByteView;
import tech.pegasys.teku.ssz.backing.view.ViewUtils;

public class SignedAggregateAndProof
    extends Container2<SignedAggregateAndProof, AggregateAndProof, SszVector<ByteView>> {

  public static class SignedAggregateAndProofType
      extends ContainerType2<SignedAggregateAndProof, AggregateAndProof, SszVector<ByteView>> {

    public SignedAggregateAndProofType() {
      super(
          "SignedAggregateAndProof",
          namedType("message", AggregateAndProof.TYPE),
          namedType("signature", ComplexViewTypes.BYTES_96_TYPE));
    }

    @Override
    public SignedAggregateAndProof createFromBackingNode(TreeNode node) {
      return new SignedAggregateAndProof(this, node);
    }
  }

  public static final SignedAggregateAndProofType TYPE = new SignedAggregateAndProofType();

  private BLSSignature signatureCache;

  private SignedAggregateAndProof(SignedAggregateAndProofType type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public SignedAggregateAndProof(final AggregateAndProof message, final BLSSignature signature) {
    super(TYPE, message, ViewUtils.createVectorFromBytes(signature.toBytesCompressed()));
    signatureCache = signature;
  }

  public AggregateAndProof getMessage() {
    return getField0();
  }

  public BLSSignature getSignature() {
    if (signatureCache == null) {
      signatureCache = BLSSignature.fromBytesCompressed(ViewUtils.getAllBytes(getField1()));
    }
    return signatureCache;
  }
}
