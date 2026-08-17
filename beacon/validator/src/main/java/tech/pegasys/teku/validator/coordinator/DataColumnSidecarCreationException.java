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

package tech.pegasys.teku.validator.coordinator;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;

/**
 * Thrown when the data column sidecars for a submitted execution payload envelope cannot be built.
 * Both cases are caused by the submission itself rather than by an internal failure, so the message
 * is safe to return to the caller as the reason for a rejected publication.
 */
public class DataColumnSidecarCreationException extends IllegalStateException {

  private final boolean blobDataNotCached;

  private DataColumnSidecarCreationException(
      final String message, final boolean blobDataNotCached) {
    super(message);
    this.blobDataNotCached = blobDataNotCached;
  }

  /**
   * The envelope was submitted without blob data ({@code Eth-Blob-Data-Included: false}) but this
   * beacon node has nothing cached to attach, typically because block production happened
   * elsewhere.
   */
  public static DataColumnSidecarCreationException noCachedBlobData(final UInt64 slot) {
    return new DataColumnSidecarCreationException(
        "No cached blobs and KZG proofs to attach to the execution payload envelope for slot "
            + slot
            + ". Block production likely happened on a different beacon node, resubmit with"
            + " Eth-Blob-Data-Included: true",
        true);
  }

  /** The submitted envelope does not carry the execution payload this beacon node built. */
  public static DataColumnSidecarCreationException cachedPayloadMismatch(final UInt64 slot) {
    return new DataColumnSidecarCreationException(
        "Signed execution payload envelope for slot "
            + slot
            + " does not match the execution payload built by this beacon node",
        false);
  }

  /**
   * Whether the failure was caused by the absence of cached blob data rather than by the submitted
   * envelope itself. Only in that case can the envelope still be published, and only when the block
   * commits to no blobs at all so that no sidecars are needed.
   */
  public boolean isBlobDataNotCached() {
    return blobDataNotCached;
  }
}
