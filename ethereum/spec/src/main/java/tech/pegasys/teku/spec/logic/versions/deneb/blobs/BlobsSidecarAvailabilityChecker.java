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

package tech.pegasys.teku.spec.logic.versions.deneb.blobs;

import static tech.pegasys.teku.spec.logic.versions.deneb.blobs.BlobsSidecarAvailabilityChecker.BlobsSidecarAndValidationResult.validResult;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobsSidecar;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;

public interface BlobsSidecarAvailabilityChecker {
  BlobsSidecarAvailabilityChecker NOOP =
      new BlobsSidecarAvailabilityChecker() {
        @Override
        public boolean initiateDataAvailabilityCheck() {
          return true;
        }

        @Override
        public SafeFuture<BlobsSidecarAndValidationResult> getAvailabilityCheckResult() {
          return NOT_REQUIRED_RESULT_FUTURE;
        }

        @Override
        public SafeFuture<BlobsSidecarAndValidationResult> validate(
            final Optional<BlobsSidecar> blobsSidecar) {
          return NOT_REQUIRED_RESULT_FUTURE;
        }
      };

  BlobsSidecarAvailabilityChecker NOT_REQUIRED = NOOP;

  Function<BlobsSidecar, BlobsSidecarAvailabilityChecker> ALREADY_CHECKED =
      (blobsSidecar) -> {
        final SafeFuture<BlobsSidecarAndValidationResult> blobsSidecarValidResult =
            SafeFuture.completedFuture(validResult(blobsSidecar));
        return new BlobsSidecarAvailabilityChecker() {
          @Override
          public boolean initiateDataAvailabilityCheck() {
            return true;
          }

          @Override
          public SafeFuture<BlobsSidecarAndValidationResult> getAvailabilityCheckResult() {
            return blobsSidecarValidResult;
          }

          @Override
          public SafeFuture<BlobsSidecarAndValidationResult> validate(
              final Optional<BlobsSidecar> blobsSidecar) {
            return blobsSidecarValidResult;
          }
        };
      };

  /**
   * Similar to {@link
   * tech.pegasys.teku.spec.logic.versions.bellatrix.block.OptimisticExecutionPayloadExecutor#optimisticallyExecute(ExecutionPayloadHeader,
   * ExecutionPayload)}
   *
   * @return true if data availability check is initiated or false to immediately fail the
   *     validation
   */
  boolean initiateDataAvailabilityCheck();

  SafeFuture<BlobsSidecarAndValidationResult> getAvailabilityCheckResult();

  /** Only perform the {@link MiscHelpersDeneb#isDataAvailable} check */
  SafeFuture<BlobsSidecarAndValidationResult> validate(Optional<BlobsSidecar> blobsSidecar);

  enum BlobsSidecarValidationResult {
    NOT_REQUIRED,
    NOT_AVAILABLE,
    INVALID,
    VALID
  }

  SafeFuture<BlobsSidecarAndValidationResult> NOT_REQUIRED_RESULT_FUTURE =
      SafeFuture.completedFuture(BlobsSidecarAndValidationResult.NOT_REQUIRED);

  class BlobsSidecarAndValidationResult {
    private final BlobsSidecarValidationResult validationResult;
    private final Optional<BlobsSidecar> blobsSidecar;
    private final Optional<Throwable> cause;

    public static final BlobsSidecarAndValidationResult NOT_AVAILABLE =
        new BlobsSidecarAndValidationResult(
            BlobsSidecarValidationResult.NOT_AVAILABLE, Optional.empty(), Optional.empty());

    public static final BlobsSidecarAndValidationResult NOT_REQUIRED =
        new BlobsSidecarAndValidationResult(
            BlobsSidecarValidationResult.NOT_REQUIRED, Optional.empty(), Optional.empty());

    public static BlobsSidecarAndValidationResult validResult(final BlobsSidecar blobsSidecar) {
      return new BlobsSidecarAndValidationResult(
          BlobsSidecarValidationResult.VALID, Optional.of(blobsSidecar), Optional.empty());
    }

    public static BlobsSidecarAndValidationResult invalidResult(final BlobsSidecar blobsSidecar) {
      return new BlobsSidecarAndValidationResult(
          BlobsSidecarValidationResult.INVALID, Optional.of(blobsSidecar), Optional.empty());
    }

    public static BlobsSidecarAndValidationResult invalidResult(
        final BlobsSidecar blobsSidecar, final Throwable cause) {
      return new BlobsSidecarAndValidationResult(
          BlobsSidecarValidationResult.INVALID, Optional.of(blobsSidecar), Optional.of(cause));
    }

    private BlobsSidecarAndValidationResult(
        final BlobsSidecarValidationResult validationResult,
        final Optional<BlobsSidecar> blobsSidecar,
        final Optional<Throwable> cause) {
      this.validationResult = validationResult;
      this.blobsSidecar = blobsSidecar;
      this.cause = cause;
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

    public boolean isNotRequired() {
      return validationResult.equals(BlobsSidecarValidationResult.NOT_REQUIRED);
    }

    public boolean isInvalid() {
      return validationResult.equals(BlobsSidecarValidationResult.INVALID);
    }

    public boolean isNotAvailable() {
      return validationResult.equals(BlobsSidecarValidationResult.NOT_AVAILABLE);
    }

    public boolean isFailure() {
      return isInvalid() || isNotAvailable();
    }

    public Optional<Throwable> getCause() {
      return cause;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final BlobsSidecarAndValidationResult that = (BlobsSidecarAndValidationResult) o;
      return Objects.equals(validationResult, that.validationResult)
          && Objects.equals(blobsSidecar, that.blobsSidecar)
          && Objects.equals(cause, that.cause);
    }

    @Override
    public int hashCode() {
      return Objects.hash(validationResult, blobsSidecar, cause);
    }
  }
}
