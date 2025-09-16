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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.gloas;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.electra.BlindedBeaconBlockBodyElectra;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestation;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;

public interface BlindedBeaconBlockBodyGloas extends BlindedBeaconBlockBodyElectra {
  static BlindedBeaconBlockBodyGloas required(final BeaconBlockBody body) {
    return body.toBlindedVersionGloas()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected an Gloas blinded block body but got "
                        + body.getClass().getSimpleName()));
  }

  @Override
  BlindedBeaconBlockBodySchemaGloas<?> getSchema();

  SignedExecutionPayloadHeader getSignedExecutionPayloadHeader();

  SszList<PayloadAttestation> getPayloadAttestations();

  @Override
  default Optional<SszList<SszKZGCommitment>> getOptionalBlobKzgCommitments() {
    return Optional.empty();
  }

  @Override
  default Optional<SignedExecutionPayloadHeader> getOptionalSignedExecutionPayloadHeader() {
    return Optional.of(getSignedExecutionPayloadHeader());
  }

  @Override
  default Optional<SszList<PayloadAttestation>> getOptionalPayloadAttestations() {
    return Optional.of(getPayloadAttestations());
  }

  @Override
  default SszList<SszKZGCommitment> getBlobKzgCommitments() {
    throw new UnsupportedOperationException("BlobKzgCommitments was removed in Gloas");
  }

  @Override
  default ExecutionRequests getExecutionRequests() {
    throw new UnsupportedOperationException("ExecutionRequests was removed in Gloas");
  }

  @Override
  default Optional<BlindedBeaconBlockBodyGloas> toBlindedVersionGloas() {
    return Optional.of(this);
  }
}
