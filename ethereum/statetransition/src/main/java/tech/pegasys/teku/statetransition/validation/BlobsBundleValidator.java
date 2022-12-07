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

package tech.pegasys.teku.statetransition.validation;

import java.util.Optional;
import java.util.stream.IntStream;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.BlobsBundle;
import tech.pegasys.teku.spec.logic.versions.eip4844.helpers.MiscHelpersEip4844;

public class BlobsBundleValidator {
  private final MiscHelpersEip4844 miscHelpers;

  public BlobsBundleValidator(final MiscHelpersEip4844 miscHelpers) {
    this.miscHelpers = miscHelpers;
  }

  // FIXME: all is optional, should we?
  public InternalValidationResult validate(
      final BlobsBundle blobsBundle, final Optional<ExecutionPayload> executionPayloadOptional) {

    // Optionally sanity-check that the KZG commitments match the versioned hashes in the
    // transactions
    if (!executionPayloadOptional
        .map(
            executionPayload ->
                miscHelpers.verifyKZGCommitmentsAgainstTransactions(
                    executionPayload.getTransactions().asList(), blobsBundle.getKzgs()))
        .orElse(true)) {
      return InternalValidationResult.reject(
          "KZG commitments doesn't match the versioned hashes in the transactions");
    }

    // Optionally sanity-check that the KZG commitments match the blobs (as produced by the
    // execution engine
    if (blobsBundle.getKzgs().size() != blobsBundle.getBlobs().size()) {
      return InternalValidationResult.reject("KZG commitments size doesn't match blobs size");
    }

    if (IntStream.range(0, blobsBundle.getBlobs().size())
        .mapToObj(
            i ->
                miscHelpers
                    .blobToKzgCommitment(blobsBundle.getBlobs().get(i))
                    .equals(blobsBundle.getKzgs().get(i)))
        .anyMatch(result -> !result)) {
      return InternalValidationResult.reject("Blobs not matching KZG commitments");
    }

    return InternalValidationResult.ACCEPT;
  }
}
