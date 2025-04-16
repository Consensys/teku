/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.validator.client.duties.attestations;

import com.google.common.base.MoreObjects;
import java.util.Optional;
import java.util.stream.Stream;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.validator.client.Validator;

public interface AggregationDutyAggregators {

  void addValidator(
      final Validator validator,
      final int validatorIndex,
      final BLSSignature proof,
      final int attestationCommitteeIndex,
      final SafeFuture<Optional<AttestationData>> unsignedAttestationFuture);

  boolean hasAggregators();

  Stream<CommitteeAggregator> streamAggregators();

  record CommitteeAggregator(
      Validator validator,
      UInt64 validatorIndex,
      UInt64 attestationCommitteeIndex,
      BLSSignature proof,
      SafeFuture<Optional<AttestationData>> unsignedAttestationFuture) {

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("validator", validator)
          .add("validatorIndex", validatorIndex)
          .add("attestationCommitteeIndex", attestationCommitteeIndex)
          .add("proof", proof)
          .add("unsignedAttestationFuture", unsignedAttestationFuture)
          .toString();
    }
  }
}
