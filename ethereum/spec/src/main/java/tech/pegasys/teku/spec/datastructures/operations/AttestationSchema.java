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

package tech.pegasys.teku.spec.datastructures.operations;

import java.util.Optional;
import java.util.function.Supplier;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.schema.SszContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitlistSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.spec.datastructures.operations.versions.electra.AttestationElectraSchema;

public interface AttestationSchema<T extends Attestation> extends SszContainerSchema<T> {

  Attestation create(
      final SszBitlist aggregationBits,
      final AttestationData data,
      final Supplier<SszBitvector> committeeBits,
      final BLSSignature signature);

  default Attestation create(
      final SszBitlist aggregationBits, final AttestationData data, final BLSSignature signature) {
    return create(aggregationBits, data, () -> null, signature);
  }

  default SszBitlist createEmptyAggregationBits() {
    final SszBitlistSchema<?> bitsSchema = getAggregationBitsSchema();
    return bitsSchema.ofBits(Math.toIntExact(bitsSchema.getMaxLength()));
  }

  default Optional<SszBitvector> createEmptyCommitteeBits() {
    return getCommitteeBitsSchema().map(SszBitvectorSchema::ofBits);
  }

  @SuppressWarnings("unchecked")
  default AttestationSchema<Attestation> castTypeToAttestationSchema() {
    return (AttestationSchema<Attestation>) this;
  }

  default Optional<AttestationElectraSchema> toVersionElectra() {
    return Optional.empty();
  }

  SszBitlistSchema<?> getAggregationBitsSchema();

  Optional<SszBitvectorSchema<?>> getCommitteeBitsSchema();

  boolean requiresCommitteeBits();
}
