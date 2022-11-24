/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.spec.logic.versions.eip4844.blobs;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.BlobsSidecar;

public interface BlobsSidecarAvailabilityChecker {
  BlobsSidecarAvailabilityChecker NOOP =
      new BlobsSidecarAvailabilityChecker() {
        @Override
        public boolean retrieveBlobsSidecar() {
          return true;
        }

        @Override
        public SafeFuture<BlobsSidecarAndValidationResult> validateBlobsSidecar() {
          return NOT_REQUIRED_RESULT;
        }
      };

  boolean retrieveBlobsSidecar();

  SafeFuture<BlobsSidecarAndValidationResult> validateBlobsSidecar();

  enum BlobsSidecarValidationResult {
    NOT_REQUIRED,
    NOT_AVAILABLE,
    INVALID,
    VALID
  }

  SafeFuture<BlobsSidecarAndValidationResult> NOT_REQUIRED_RESULT =
      SafeFuture.completedFuture(
          new BlobsSidecarAndValidationResult(
              BlobsSidecarValidationResult.NOT_REQUIRED, Optional.empty()));

  SafeFuture<BlobsSidecarAndValidationResult> NOT_AVAILABLE_RESULT =
      SafeFuture.completedFuture(BlobsSidecarAndValidationResult.NOT_AVAILABLE);

  static SafeFuture<BlobsSidecarAndValidationResult> validResult(
      final Optional<BlobsSidecar> blobsSidecar) {
    return SafeFuture.completedFuture(
        new BlobsSidecarAndValidationResult(BlobsSidecarValidationResult.VALID, blobsSidecar));
  }

  static SafeFuture<BlobsSidecarAndValidationResult> invalidResult(
      final Optional<BlobsSidecar> blobsSidecar) {
    return SafeFuture.completedFuture(
        new BlobsSidecarAndValidationResult(BlobsSidecarValidationResult.INVALID, blobsSidecar));
  }

  class BlobsSidecarAndValidationResult {
    private final BlobsSidecarValidationResult validationResult;
    private final Optional<BlobsSidecar> blobsSidecar;

    private static final BlobsSidecarAndValidationResult NOT_AVAILABLE =
        new BlobsSidecarAndValidationResult(
            BlobsSidecarValidationResult.NOT_AVAILABLE, Optional.empty());

    public BlobsSidecarAndValidationResult(
        final BlobsSidecarValidationResult validationResult,
        final Optional<BlobsSidecar> blobsSidecar) {
      this.validationResult = validationResult;
      this.blobsSidecar = blobsSidecar;
    }

    public BlobsSidecarValidationResult getValidationResult() {
      return validationResult;
    }

    public Optional<BlobsSidecar> getBlobsSidecar() {
      return blobsSidecar;
    }

    public boolean isValid() {
      return validationResult.equals(BlobsSidecarValidationResult.VALID);
    }

    public boolean isFailure() {
      return validationResult.equals(BlobsSidecarValidationResult.INVALID)
          || validationResult.equals(BlobsSidecarValidationResult.NOT_AVAILABLE);
    }

    public boolean isNotRequired() {
      return validationResult.equals(BlobsSidecarValidationResult.NOT_REQUIRED);
    }
  }
}
