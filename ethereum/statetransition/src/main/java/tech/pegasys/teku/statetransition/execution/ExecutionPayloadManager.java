/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.statetransition.execution;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.logic.common.statetransition.results.ExecutionPayloadImportResult;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public interface ExecutionPayloadManager {

  ExecutionPayloadManager NOOP =
      new ExecutionPayloadManager() {
        @Override
        public boolean isExecutionPayloadRecentlySeen(final Bytes32 beaconBlockRoot) {
          return false;
        }

        @Override
        public SafeFuture<InternalValidationResult> validateAndImportExecutionPayload(
            final SignedExecutionPayloadEnvelope signedExecutionPayload,
            final Optional<UInt64> arrivalTimestamp) {
          return SafeFuture.completedFuture(InternalValidationResult.ACCEPT);
        }

        @Override
        public SafeFuture<ExecutionPayloadImportResult> importExecutionPayload(
            final SignedExecutionPayloadEnvelope signedExecutionPayload) {
          return SafeFuture.completedFuture(
              ExecutionPayloadImportResult.successful(signedExecutionPayload));
        }
      };

  /**
   * {@link SignedExecutionPayloadEnvelope} has been recently seen referencing the block. This
   * method is used for the `payload_present` vote.
   */
  boolean isExecutionPayloadRecentlySeen(Bytes32 beaconBlockRoot);

  /**
   * Performs gossip validation on the {@code signedExecutionPayload} and imports it async if
   * accepted
   *
   * @return the gossip validation result
   */
  SafeFuture<InternalValidationResult> validateAndImportExecutionPayload(
      SignedExecutionPayloadEnvelope signedExecutionPayload, Optional<UInt64> arrivalTimestamp);

  /**
   * Imports execution payload via fork choice `on_execution_payload`
   *
   * @return the execution payload import result
   */
  SafeFuture<ExecutionPayloadImportResult> importExecutionPayload(
      final SignedExecutionPayloadEnvelope signedExecutionPayload);

  default SafeFuture<InternalValidationResult> validateAndImportExecutionPayload(
      final SignedExecutionPayloadEnvelope signedExecutionPayload) {
    return validateAndImportExecutionPayload(signedExecutionPayload, Optional.empty());
  }
}
