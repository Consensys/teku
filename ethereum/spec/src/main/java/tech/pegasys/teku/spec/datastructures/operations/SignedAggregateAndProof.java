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

package tech.pegasys.teku.spec.datastructures.operations;

import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ssz.backing.SszVector;
import tech.pegasys.teku.ssz.backing.containers.Container2;
import tech.pegasys.teku.ssz.backing.containers.ContainerSchema2;
import tech.pegasys.teku.ssz.backing.schema.SszComplexSchemas;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszByte;
import tech.pegasys.teku.ssz.backing.view.SszUtils;

public class SignedAggregateAndProof
    extends Container2<SignedAggregateAndProof, AggregateAndProof, SszVector<SszByte>> {

  public static class SignedAggregateAndProofSchema
      extends ContainerSchema2<SignedAggregateAndProof, AggregateAndProof, SszVector<SszByte>> {

    public SignedAggregateAndProofSchema() {
      super(
          "SignedAggregateAndProof",
          namedSchema("message", AggregateAndProof.SSZ_SCHEMA),
          namedSchema("signature", SszComplexSchemas.BYTES_96_SCHEMA));
    }

    @Override
    public SignedAggregateAndProof createFromBackingNode(TreeNode node) {
      return new SignedAggregateAndProof(this, node);
    }
  }

  public static final SignedAggregateAndProofSchema SSZ_SCHEMA =
      new SignedAggregateAndProofSchema();

  private BLSSignature signatureCache;

  private SignedAggregateAndProof(SignedAggregateAndProofSchema type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public SignedAggregateAndProof(final AggregateAndProof message, final BLSSignature signature) {
    super(SSZ_SCHEMA, message, SszUtils.toSszByteVector(signature.toBytesCompressed()));
    signatureCache = signature;
  }

  public AggregateAndProof getMessage() {
    return getField0();
  }

  public BLSSignature getSignature() {
    if (signatureCache == null) {
      signatureCache = BLSSignature.fromBytesCompressed(SszUtils.getAllBytes(getField1()));
    }
    return signatureCache;
  }
}
