/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.statetransition.attestation;

import tech.pegasys.teku.core.results.AttestationProcessingResult;
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.storage.Store;
import tech.pegasys.teku.storage.client.RecentChainData;

public class ForkChoiceAttestationProcessor {

  private final RecentChainData recentChainData;
  private final ForkChoice forkChoice;

  public ForkChoiceAttestationProcessor(
      final RecentChainData recentChainData, final ForkChoice forkChoice) {
    this.recentChainData = recentChainData;
    this.forkChoice = forkChoice;
  }

  public AttestationProcessingResult processAttestation(final ValidateableAttestation attestation) {
    final Store.Transaction transaction = recentChainData.startStoreTransaction();
    final AttestationProcessingResult result = forkChoice.onAttestation(transaction, attestation);
    if (result.isSuccessful()) {
      transaction.commit(() -> {}, "Failed to persist attestation result");
    }
    return result;
  }

  public void applyIndexedAttestationToForkChoice(final IndexedAttestation attestation) {
    final Store.Transaction transaction = recentChainData.startStoreTransaction();
    forkChoice.applyIndexedAttestation(transaction, attestation);
    transaction.commit(() -> {}, "Failed to persist attestation result");
  }
}
