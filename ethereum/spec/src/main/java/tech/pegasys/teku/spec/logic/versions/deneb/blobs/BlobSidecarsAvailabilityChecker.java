/*
 * Copyright Consensys Software Inc., 2023
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

package tech.pegasys.teku.spec.logic.versions.deneb.blobs;

import static tech.pegasys.teku.spec.logic.versions.deneb.blobs.BlobSidecarsAndValidationResult.NOT_REQUIRED_RESULT_FUTURE;

import java.util.List;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecarOld;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.NewPayloadRequest;
import tech.pegasys.teku.spec.logic.versions.bellatrix.block.OptimisticExecutionPayloadExecutor;

public interface BlobSidecarsAvailabilityChecker {

  BlobSidecarsAvailabilityChecker NOOP =
      new BlobSidecarsAvailabilityChecker() {
        @Override
        public boolean initiateDataAvailabilityCheck() {
          return true;
        }

        @Override
        public SafeFuture<BlobSidecarsAndValidationResult> getAvailabilityCheckResult() {
          return NOT_REQUIRED_RESULT_FUTURE;
        }

        @Override
        public BlobSidecarsAndValidationResult validateImmediately(
            final List<BlobSidecarOld> blobSidecars) {
          return BlobSidecarsAndValidationResult.NOT_REQUIRED;
        }
      };

  BlobSidecarsAvailabilityChecker NOT_REQUIRED = NOOP;

  /**
   * Similar to {@link OptimisticExecutionPayloadExecutor#optimisticallyExecute(
   * ExecutionPayloadHeader, NewPayloadRequest)}
   *
   * @return true if data availability check is initiated or false to immediately fail the
   *     validation
   */
  boolean initiateDataAvailabilityCheck();

  SafeFuture<BlobSidecarsAndValidationResult> getAvailabilityCheckResult();

  /** Perform the data availability check immediately on the provided blob sidecars */
  BlobSidecarsAndValidationResult validateImmediately(List<BlobSidecarOld> blobSidecars);
}
