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

package tech.pegasys.teku.statetransition.execution;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.logic.common.statetransition.results.ExecutionPayloadImportResult;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public interface ExecutionPayloadManager {

  ExecutionPayloadManager NOOP =
      new ExecutionPayloadManager() {
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

  SafeFuture<InternalValidationResult> validateAndImportExecutionPayload(
      SignedExecutionPayloadEnvelope signedExecutionPayload, Optional<UInt64> arrivalTimestamp);

  SafeFuture<ExecutionPayloadImportResult> importExecutionPayload(
      SignedExecutionPayloadEnvelope signedExecutionPayload);
}
