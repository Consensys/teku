/*
 * Copyright Consensys Software Inc., 2022
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
import tech.pegasys.teku.infrastructure.ssz.containers.Container3;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.operations.Attestation.AttestationSchema;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.datastructures.type.SszSignatureSchema;

public class AggregateAndProof
    extends Container3<AggregateAndProof, SszUInt64, Attestation, SszSignature> {

  public static class AggregateAndProofSchema
      extends ContainerSchema3<AggregateAndProof, SszUInt64, Attestation, SszSignature> {

    public AggregateAndProofSchema(final SpecConfig specConfig) {
      super(
          "AggregateAndProof",
          namedSchema("aggregator_index", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("aggregate", new AttestationSchema(specConfig)),
          namedSchema("selection_proof", SszSignatureSchema.INSTANCE));
    }

    public AttestationSchema getAttestationSchema() {
      return (AttestationSchema) getFieldSchema1();
    }

    @Override
    public AggregateAndProof createFromBackingNode(final TreeNode node) {
      return new AggregateAndProof(this, node);
    }

    public AggregateAndProof create(
        final UInt64 index, final Attestation aggregate, final BLSSignature selectionProof) {
      return new AggregateAndProof(this, index, aggregate, selectionProof);
    }
  }

  private AggregateAndProof(final AggregateAndProofSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  private AggregateAndProof(
      final AggregateAndProofSchema schema,
      final UInt64 index,
      final Attestation aggregate,
      final BLSSignature selectionProof) {
    super(schema, SszUInt64.of(index), aggregate, new SszSignature(selectionProof));
  }

  public UInt64 getIndex() {
    return getField0().get();
  }

  public Attestation getAggregate() {
    return getField1();
  }

  public BLSSignature getSelectionProof() {
    return getField2().getSignature();
  }

  @Override
  public AggregateAndProofSchema getSchema() {
    return (AggregateAndProofSchema) super.getSchema();
  }
}
