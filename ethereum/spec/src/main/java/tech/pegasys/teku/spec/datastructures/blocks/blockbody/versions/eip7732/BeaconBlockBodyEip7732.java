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
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.electra.BeaconBlockBodyElectra;
import tech.pegasys.teku.spec.datastructures.execution.SignedExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionPayloadElectra;
import tech.pegasys.teku.spec.datastructures.operations.PayloadAttestation;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;

public interface BeaconBlockBodyEip7732 extends BeaconBlockBodyElectra {
  static BeaconBlockBodyEip7732 required(final BeaconBlockBody body) {
    return body.toVersionEip7732()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected Eip7732 block body but got " + body.getClass().getSimpleName()));
  }

  @Override
  BeaconBlockBodySchemaEip7732<?> getSchema();

  SignedExecutionPayloadHeader getSignedExecutionPayloadHeader();

  SszList<PayloadAttestation> getPayloadAttestations();

  @Override
  default Optional<SignedExecutionPayloadHeader> getOptionalSignedExecutionPayloadHeader() {
    return Optional.of(getSignedExecutionPayloadHeader());
  }

  @Override
  default Optional<SszList<PayloadAttestation>> getOptionalPayloadAttestations() {
    return Optional.of(getPayloadAttestations());
  }

  @Override
  default ExecutionPayloadElectra getExecutionPayload() {
    throw new UnsupportedOperationException("ExecutionPayload removed in Eip7732");
  }

  @Override
  default SszList<SszKZGCommitment> getBlobKzgCommitments() {
    throw new UnsupportedOperationException("BlobKzgCommitments removed in Eip7732");
  }

  @Override
  default Optional<BeaconBlockBodyEip7732> toVersionEip7732() {
    return Optional.of(this);
  }
}
