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
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.datastructures.type.SszSignatureSchema;

public class PayloadAttestationSchema
    extends ContainerSchema3<
        PayloadAttestation, SszBitvector, PayloadAttestationData, SszSignature> {

  public PayloadAttestationSchema(final long ptcSize) {
    super(
        "PayloadAttestation",
        namedSchema("aggregation_bits", SszBitvectorSchema.create(ptcSize)),
        namedSchema("data", PayloadAttestationData.SSZ_SCHEMA),
        namedSchema("signature", SszSignatureSchema.INSTANCE));
  }

  public PayloadAttestation create(
      final SszBitvector aggregationBits,
      final PayloadAttestationData data,
      final BLSSignature signature) {
    return new PayloadAttestation(this, aggregationBits, data, signature);
  }

  @Override
  public PayloadAttestation createFromBackingNode(final TreeNode node) {
    return new PayloadAttestation(this, node);
  }

  public SszBitvectorSchema<?> getAggregationBitsSchema() {
    return (SszBitvectorSchema<?>) getFieldSchema0();
  }

  public SszBitvector createEmptyAggregationBits() {
    final SszBitvectorSchema<?> bitvectorSchema = getAggregationBitsSchema();
    return bitvectorSchema.ofBits(Math.toIntExact(bitvectorSchema.getMaxLength()));
  }
}
