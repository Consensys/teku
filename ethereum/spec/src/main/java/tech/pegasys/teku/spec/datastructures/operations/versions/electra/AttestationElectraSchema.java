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

import java.util.Optional;
import java.util.function.Supplier;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;

public interface AttestationElectraSchema extends AttestationSchema<AttestationElectra> {

  @Override
  default Attestation create(
      final SszBitlist aggregationBits,
      final AttestationData data,
      final BLSSignature signature,
      final Supplier<SszBitvector> committeeBits) {
    final SszBitvector suppliedCommitteeBits = committeeBits.get();
    checkNotNull(suppliedCommitteeBits, "committeeBits must be provided in Electra");
    return create(aggregationBits, data, new SszSignature(signature), suppliedCommitteeBits);
  }

  AttestationElectra create(
      final SszBitlist aggregationBits,
      final AttestationData data,
      final SszSignature signature,
      final SszBitvector committeeBits);

  @Override
  default Optional<AttestationElectraSchema> toVersionElectra() {
    return Optional.of(this);
  }

  @Override
  default boolean requiresCommitteeBits() {
    return true;
  }
}
