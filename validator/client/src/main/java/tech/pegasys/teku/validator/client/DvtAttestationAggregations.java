/*
 * Copyright Consensys Software Inc., 2024
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

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ethereum.json.types.validator.BeaconCommitteeSelectionProof;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;

public class DvtAttestationAggregations {

  // TODO-lucas add a limit on the number of entries in batch request

  private static final Logger LOG = LogManager.getLogger();

  private final ValidatorApiChannel validatorApiChannel;

  private final Map<BeaconCommitteeSelectionProof, SafeFuture<BLSSignature>> pendingRequests =
      new ConcurrentHashMap<>();

  private final int expectedCount;

  public DvtAttestationAggregations(
      final ValidatorApiChannel validatorApiChannel, int expectedDutiesCount) {
    this.validatorApiChannel = validatorApiChannel;
    this.expectedCount = expectedDutiesCount;
  }

  public SafeFuture<BLSSignature> getCombinedSelectionProofFuture(
      final int validatorIndex, final UInt64 slot, final BLSSignature partialProof) {

    final BeaconCommitteeSelectionProof request =
        new BeaconCommitteeSelectionProof.Builder()
            .validatorIndex(validatorIndex)
            .slot(slot)
            .selectionProof(partialProof.toBytesCompressed().toHexString())
            .build();

    final SafeFuture<BLSSignature> future = new SafeFuture<>();
    pendingRequests.put(request, future);

    if (pendingRequests.size() >= expectedCount) {
      submitBatchRequests();
    }

    return future;
  }

  private void submitBatchRequests() {
    validatorApiChannel
        .getBeaconCommitteeSelectionProof(pendingRequests.keySet().stream().toList())
        .thenAccept(
            beaconCommitteeSelectionProofs -> {
              if (beaconCommitteeSelectionProofs.isPresent()) {
                pendingRequests.forEach(
                    (request, future) -> {
                      final Optional<BeaconCommitteeSelectionProof> response =
                          beaconCommitteeSelectionProofs.get().stream()
                              .filter(
                                  p ->
                                      request.getValidatorIndex() == p.getValidatorIndex()
                                          && request.getSlot().equals(p.getSlot()))
                              .findFirst();
                      response.ifPresentOrElse(
                          resp -> future.complete(resp.getSelectionProofSignature()),
                          () -> future.completeExceptionally(new RuntimeException("Error")));
                    });
              } else {
                pendingRequests
                    .values()
                    .forEach(future -> future.completeExceptionally(new RuntimeException("Error")));
              }
            })
        .ifExceptionGetsHereRaiseABug();
  }
}
