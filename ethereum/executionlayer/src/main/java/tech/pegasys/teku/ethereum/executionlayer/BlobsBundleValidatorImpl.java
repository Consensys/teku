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

package tech.pegasys.teku.ethereum.executionlayer;

import java.util.Optional;
import java.util.stream.IntStream;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobsBundle;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;

public class BlobsBundleValidatorImpl implements BlobsBundleValidator {
  private final MiscHelpersDeneb miscHelpers;

  public BlobsBundleValidatorImpl(final MiscHelpersDeneb miscHelpers) {
    this.miscHelpers = miscHelpers;
  }

  @Override
  public void validate(
      final BlobsBundle blobsBundle, final Optional<ExecutionPayload> executionPayloadOptional)
      throws BlobsBundleValidationException {

    // Optionally sanity-check that the KZG commitments match the versioned hashes in the
    // transactions
    if (!executionPayloadOptional
        .map(
            executionPayload ->
                miscHelpers.verifyKZGCommitmentsAgainstTransactions(
                    executionPayload.getTransactions().asList(), blobsBundle.getCommitments()))
        .orElse(true)) {
      throw new BlobsBundleValidationException(
          "KZG commitments doesn't match the versioned hashes in the transactions");
    }

    // Optionally sanity-check that the KZG commitments match the blobs (as produced by the
    // execution engine
    if (blobsBundle.getCommitments().size() != blobsBundle.getBlobs().size()) {
      throw new BlobsBundleValidationException("KZG commitments size doesn't match blobs size");
    }

    if (IntStream.range(0, blobsBundle.getBlobs().size())
        .mapToObj(
            i ->
                miscHelpers
                    .blobToKzgCommitment(blobsBundle.getBlobs().get(i))
                    .equals(blobsBundle.getCommitments().get(i)))
        .anyMatch(result -> !result)) {
      throw new BlobsBundleValidationException("Blobs not matching KZG commitments");
    }
  }
}
