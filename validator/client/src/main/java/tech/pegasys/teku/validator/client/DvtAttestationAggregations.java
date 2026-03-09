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

package tech.pegasys.teku.validator.client;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ethereum.json.types.validator.BeaconCommitteeSelectionProof;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;

public class DvtAttestationAggregations {

  private static final Logger LOG = LogManager.getLogger();

  private final ValidatorApiChannel validatorApiChannel;
  private final Map<BeaconCommitteeSelectionProof, SafeFuture<BLSSignature>> pendingRequests =
      new ConcurrentHashMap<>();
  private final int expectedDutiesCount;

  public DvtAttestationAggregations(
      final ValidatorApiChannel validatorApiChannel, final int expectedDutiesCount) {
    this.validatorApiChannel = validatorApiChannel;
    this.expectedDutiesCount = expectedDutiesCount;
  }

  public SafeFuture<BLSSignature> getCombinedSelectionProofFuture(
      final int validatorIndex, final UInt64 slot, final BLSSignature partialProof) {

    LOG.debug(
        "Created pending request for DVT attestation aggregation proof (validator_id={}, slot={})",
        validatorIndex,
        slot);

    final BeaconCommitteeSelectionProof request =
        new BeaconCommitteeSelectionProof.Builder()
            .validatorIndex(validatorIndex)
            .slot(slot)
            .selectionProof(partialProof.toBytesCompressed().toHexString())
            .build();

    final SafeFuture<BLSSignature> future = new SafeFuture<>();
    pendingRequests.put(request, future);

    if (pendingRequests.size() >= expectedDutiesCount) {
      submitBatchRequests(slot);
    }

    return future;
  }

  private void submitBatchRequests(final UInt64 slot) {
    validatorApiChannel
        .getBeaconCommitteeSelectionProof(pendingRequests.keySet().stream().toList())
        .thenAccept(
            response ->
                response.ifPresentOrElse(
                    this::handleBeaconCommitteeSelectionProofsResponse,
                    () -> handleEmptyResponse(slot)))
        .exceptionally(unexpectedErrorHandler())
        .finishStackTrace();
  }

  private void handleBeaconCommitteeSelectionProofsResponse(
      final List<BeaconCommitteeSelectionProof> proofsFromMiddleware) {
    LOG.debug("Processing response from middleware for {} requests", proofsFromMiddleware.size());

    pendingRequests.forEach(
        (request, future) -> {
          final Optional<BeaconCommitteeSelectionProof> completeProof =
              proofsFromMiddleware.stream().filter(matchingRequest(request)).findFirst();

          completeProof.ifPresentOrElse(
              resp -> {
                LOG.debug(
                    "Completing request for DVT attestation aggregation proof (validator_id={}, slot={})",
                    request.getValidatorIndex(),
                    request.getSlot());

                future.complete(resp.getSelectionProofSignature());
              },
              () ->
                  future.completeExceptionally(
                      new RuntimeException(
                          "No matching complete proof from DVT middleware for this request")));
        });
  }

  private static Predicate<BeaconCommitteeSelectionProof> matchingRequest(
      final BeaconCommitteeSelectionProof request) {
    return proof ->
        request.getValidatorIndex() == proof.getValidatorIndex()
            && request.getSlot().equals(proof.getSlot());
  }

  private void handleEmptyResponse(final UInt64 slot) {
    LOG.warn(
        "Received empty response from DVT middleware for slot {}. This will impact aggregation duties.",
        slot);
    completeAllPendingFuturesExceptionally("Empty response from DVT middleware for slot " + slot);
  }

  private Function<Throwable, Void> unexpectedErrorHandler() {
    return (ex) -> {
      final String errorMsg = "Error getting DVT attestation aggregation complete proof";
      LOG.warn(errorMsg);
      LOG.debug(errorMsg, ex);

      completeAllPendingFuturesExceptionally(errorMsg);
      return null;
    };
  }

  private void completeAllPendingFuturesExceptionally(final String errorMsg) {
    pendingRequests
        .values()
        .forEach(
            future -> {
              if (!future.isDone()) {
                future.completeExceptionally(new RuntimeException(errorMsg));
              }
            });
  }
}
