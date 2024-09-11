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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip7732;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.electra.BlindedBeaconBlockBodyElectra;
import tech.pegasys.teku.spec.datastructures.execution.SignedExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.operations.PayloadAttestation;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;

public interface BlindedBeaconBlockBodyEip7732 extends BlindedBeaconBlockBodyElectra {
  static BlindedBeaconBlockBodyEip7732 required(final BeaconBlockBody body) {
    return body.toBlindedVersionEip7732()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected an Eip7732 blinded block body but got "
                        + body.getClass().getSimpleName()));
  }

  @Override
  BlindedBeaconBlockBodySchemaEip7732<?> getSchema();

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
    throw new UnsupportedOperationException("BlobKzgCommitments removed in Eip7732");
  }

  @Override
  default Optional<BlindedBeaconBlockBodyEip7732> toBlindedVersionEip7732() {
    return Optional.of(this);
  }
}
