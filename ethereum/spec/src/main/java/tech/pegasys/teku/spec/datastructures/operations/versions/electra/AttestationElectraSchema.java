/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.spec.datastructures.operations.versions.electra;

import static com.google.common.base.Preconditions.checkNotNull;
import static tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszContainerSchema.namedSchema;
import static tech.pegasys.teku.spec.datastructures.StableContainerCapacities.MAX_ATTESTATION_FIELDS;

import java.util.Optional;
import java.util.function.Supplier;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.containers.ProfileSchema4;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitlistSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.datastructures.type.SszSignatureSchema;

public class AttestationElectraSchema
    extends ProfileSchema4<
        AttestationElectra, SszBitlist, AttestationData, SszSignature, SszBitvector>
    implements AttestationSchema<AttestationElectra> {

  public AttestationElectraSchema(
      final long maxValidatorsPerAttestation, final long maxCommitteePerSlot) {
    super(
        "AttestationElectra",
        namedSchema("aggregation_bits", SszBitlistSchema.create(maxValidatorsPerAttestation)),
        namedSchema("data", AttestationData.SSZ_SCHEMA),
        namedSchema("signature", SszSignatureSchema.INSTANCE),
        namedSchema("committee_bits", SszBitvectorSchema.create(maxCommitteePerSlot)),
        MAX_ATTESTATION_FIELDS);
  }

  @Override
  public SszBitlistSchema<?> getAggregationBitsSchema() {
    return (SszBitlistSchema<?>) getFieldSchema0();
  }

  @Override
  public Optional<SszBitvectorSchema<?>> getCommitteeBitsSchema() {
    return Optional.of((SszBitvectorSchema<?>) getFieldSchema3());
  }

  @Override
  public AttestationElectra createFromBackingNode(final TreeNode node) {
    return new AttestationElectra(this, node);
  }

  @Override
  public Attestation create(
      final SszBitlist aggregationBits,
      final AttestationData data,
      final BLSSignature signature,
      final Supplier<SszBitvector> committeeBits) {
    final SszBitvector suppliedCommitteeBits = committeeBits.get();
    checkNotNull(suppliedCommitteeBits, "committeeBits must be provided in Electra");
    return new AttestationElectra(this, aggregationBits, data, signature, suppliedCommitteeBits);
  }

  public AttestationElectra create(
      final SszBitlist aggregationBits,
      final AttestationData data,
      final BLSSignature signature,
      final SszBitvector committeeBits) {
    return new AttestationElectra(this, aggregationBits, data, signature, committeeBits);
  }

  @Override
  public Optional<AttestationElectraSchema> toVersionElectra() {
    return Optional.of(this);
  }

  @Override
  public boolean requiresCommitteeBits() {
    return true;
  }
}
