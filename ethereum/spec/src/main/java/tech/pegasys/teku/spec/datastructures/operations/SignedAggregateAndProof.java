/*
 * Copyright ConsenSys Software Inc., 2022
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
import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.operations.AggregateAndProof.AggregateAndProofSchema;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.datastructures.type.SszSignatureSchema;

public class SignedAggregateAndProof
    extends Container2<SignedAggregateAndProof, AggregateAndProof, SszSignature> {

  public static class SignedAggregateAndProofSchema
      extends ContainerSchema2<SignedAggregateAndProof, AggregateAndProof, SszSignature> {

    public SignedAggregateAndProofSchema(final SpecConfig specConfig) {
      super(
          "SignedAggregateAndProof",
          namedSchema("message", new AggregateAndProofSchema(specConfig)),
          namedSchema("signature", SszSignatureSchema.INSTANCE));
    }

    public AggregateAndProofSchema getAggregateAndProofSchema() {
      return (AggregateAndProofSchema) getFieldSchema0();
    }

    @Override
    public SignedAggregateAndProof createFromBackingNode(TreeNode node) {
      return new SignedAggregateAndProof(this, node);
    }

    public SignedAggregateAndProof create(final AggregateAndProof message, BLSSignature signature) {
      return new SignedAggregateAndProof(this, message, signature);
    }
  }

  private SignedAggregateAndProof(SignedAggregateAndProofSchema type, TreeNode backingNode) {
    super(type, backingNode);
  }

  private SignedAggregateAndProof(
      final SignedAggregateAndProofSchema schema,
      final AggregateAndProof message,
      final BLSSignature signature) {
    super(schema, message, new SszSignature(signature));
  }

  @Override
  public SignedAggregateAndProofSchema getSchema() {
    return (SignedAggregateAndProofSchema) super.getSchema();
  }

  public AggregateAndProof getMessage() {
    return getField0();
  }

  public BLSSignature getSignature() {
    return getField1().getSignature();
  }
}
