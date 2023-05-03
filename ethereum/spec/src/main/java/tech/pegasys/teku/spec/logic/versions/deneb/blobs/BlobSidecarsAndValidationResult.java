/*
 * Copyright ConsenSys Software Inc., 2023
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

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;

public class BlobSidecarsAndValidationResult {

  private final BlobSidecarsValidationResult validationResult;
  private final List<BlobSidecar> blobSidecars;
  private final Optional<Throwable> cause;

  public static final BlobSidecarsAndValidationResult NOT_AVAILABLE =
      new BlobSidecarsAndValidationResult(
          BlobSidecarsValidationResult.NOT_AVAILABLE, Collections.emptyList(), Optional.empty());

  public static final BlobSidecarsAndValidationResult NOT_REQUIRED =
      new BlobSidecarsAndValidationResult(
          BlobSidecarsValidationResult.NOT_REQUIRED, Collections.emptyList(), Optional.empty());

  public static final SafeFuture<BlobSidecarsAndValidationResult> NOT_REQUIRED_RESULT_FUTURE =
      SafeFuture.completedFuture(NOT_REQUIRED);

  public static BlobSidecarsAndValidationResult validResult(final List<BlobSidecar> blobSidecars) {
    return new BlobSidecarsAndValidationResult(
        BlobSidecarsValidationResult.VALID, blobSidecars, Optional.empty());
  }

  public static BlobSidecarsAndValidationResult invalidResult(
      final List<BlobSidecar> blobSidecars) {
    return new BlobSidecarsAndValidationResult(
        BlobSidecarsValidationResult.INVALID, blobSidecars, Optional.empty());
  }

  public static BlobSidecarsAndValidationResult invalidResult(
      final List<BlobSidecar> blobSidecars, final Throwable cause) {
    return new BlobSidecarsAndValidationResult(
        BlobSidecarsValidationResult.INVALID, blobSidecars, Optional.of(cause));
  }

  public static BlobSidecarsAndValidationResult notAvailable(final Throwable cause) {
    return new BlobSidecarsAndValidationResult(
        BlobSidecarsValidationResult.NOT_AVAILABLE, Collections.emptyList(), Optional.of(cause));
  }

  private BlobSidecarsAndValidationResult(
      final BlobSidecarsValidationResult validationResult,
      final List<BlobSidecar> blobSidecars,
      final Optional<Throwable> cause) {
    this.validationResult = validationResult;
    this.blobSidecars = blobSidecars;
    this.cause = cause;
  }

  public BlobSidecarsValidationResult getValidationResult() {
    return validationResult;
  }

  public List<BlobSidecar> getBlobSidecars() {
    return blobSidecars;
  }

  public Optional<Throwable> getCause() {
    return cause;
  }

  public boolean isValid() {
    return validationResult.equals(BlobSidecarsValidationResult.VALID);
  }

  public boolean isNotRequired() {
    return validationResult.equals(BlobSidecarsValidationResult.NOT_REQUIRED);
  }

  public boolean isInvalid() {
    return validationResult.equals(BlobSidecarsValidationResult.INVALID);
  }

  public boolean isNotAvailable() {
    return validationResult.equals(BlobSidecarsValidationResult.NOT_AVAILABLE);
  }

  public boolean isFailure() {
    return isInvalid() || isNotAvailable();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final BlobSidecarsAndValidationResult that = (BlobSidecarsAndValidationResult) o;
    return Objects.equals(validationResult, that.validationResult)
        && Objects.equals(blobSidecars, that.blobSidecars)
        && Objects.equals(cause, that.cause);
  }

  @Override
  public int hashCode() {
    return Objects.hash(validationResult, blobSidecars, cause);
  }

  public String toLogString() {
    return String.format(
        "result %s, blob sidecars: %d, cause: %s",
        validationResult, blobSidecars.size(), cause.map(ExceptionUtil::getMessageOrSimpleName));
  }
}
