/*
 * Copyright Consensys Software Inc., 2025
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

import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.SpecConfigEip7805;
import tech.pegasys.teku.spec.datastructures.operations.InclusionList;
import tech.pegasys.teku.spec.datastructures.operations.SignedInclusionList;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.util.InclusionListUtil;
import tech.pegasys.teku.storage.client.RecentChainData;

public class SignedInclusionListValidator {

  private final Spec spec;
  private final RecentChainData recentChainData;
  private final AsyncBLSSignatureVerifier signatureVerifier;

  public SignedInclusionListValidator(
      final Spec spec,
      final RecentChainData recentChainData,
      final AsyncBLSSignatureVerifier signatureVerifier) {
    this.spec = spec;
    this.recentChainData = recentChainData;
    this.signatureVerifier = signatureVerifier;
  }

  public SafeFuture<InternalValidationResult> validate(
      final SignedInclusionList signedInclusionList,
      final NavigableMap<UInt64, ConcurrentMap<UInt64, List<SignedInclusionList>>>
          slotToInclusionListsByValidatorIndex) {

    final InclusionList inclusionList = signedInclusionList.getMessage();
    final UInt64 slot = inclusionList.getSlot();
    final SpecConfigEip7805 specConfigEip7805 =
        spec.atSlot(slot).getConfig().toVersionEip7805().orElseThrow();
    final int maxBytesPerInclusionList = specConfigEip7805.getMaxBytesPerInclusionList();
    final int transactionsBytesSize =
        inclusionList.getTransactions().stream()
            .map(transaction -> transaction.getBytes().size())
            .reduce(0, Integer::sum);

    /*
     * [REJECT] The size of message is within upperbound MAX_BYTES_PER_INCLUSION_LIST
     */
    if (transactionsBytesSize > maxBytesPerInclusionList) {
      return SafeFuture.completedFuture(
          InternalValidationResult.reject(
              "Inclusion List's transactions size %d (bytes) exceeds max allowed size %d (bytes)",
              transactionsBytesSize, maxBytesPerInclusionList));
    }

    final UInt64 genesisTime = recentChainData.getGenesisTime();
    final UInt64 currentTimeMillis = recentChainData.getStore().getTimeInMillis();
    final InclusionListUtil inclusionListUtil =
        spec.atSlot(slot).getInclusionListUtil().orElseThrow();

    /*
     * [REJECT] The slot message.slot is equal to the previous or current slot.
     */
    if (!inclusionListUtil.isInclusionListForCurrentOrPreviousSlot(
        slot, genesisTime, currentTimeMillis)) {
      return SafeFuture.completedFuture(
          InternalValidationResult.reject("Inclusion List should be for current or previous slot"));
    }

    /*
     * [IGNORE] The slot message.slot is equal to the current slot, or it is equal to the previous slot and the current time is less than ATTESTATION_DEADLINE seconds into the slot.
     */
    if (!inclusionListUtil.isInclusionListWithinDeadline(slot, genesisTime, currentTimeMillis)) {
      return SafeFuture.completedFuture(
          InternalValidationResult.ignore("Inclusion List is beyond attestation deadline"));
    }

    /*
     * [IGNORE] The message is either the first or second valid message received from the validator with index message.validator_index.
     */
    if (countInclusionLists(slotToInclusionListsByValidatorIndex, inclusionList.getValidatorIndex())
        > 2) {
      return SafeFuture.completedFuture(
          InternalValidationResult.ignore(
              "Already received 2 Inclusion Lists from validator with index %d",
              inclusionList.getValidatorIndex().intValue()));
    }

    return recentChainData
        .retrieveStateInEffectAtSlot(slot)
        .thenCompose(
            maybeState -> {
              if (maybeState.isEmpty()) {
                // We know the block is imported but now don't have a state to validate against
                // Must have got pruned between checks
                return SafeFuture.completedFuture(InternalValidationResult.IGNORE);
              }
              final BeaconState state = maybeState.get();
              /*
               * [IGNORE] The inclusion_list_committee for slot message.slot on the current branch corresponds to message.inclusion_list_committee_root, as determined by hash_tree_root(inclusion_list_committee) == message.inclusion_list_committee_root.
               */
              if (!inclusionListUtil.hasCorrectCommitteeRoot(
                  state, slot, inclusionList.getInclusionListCommitteeRoot())) {
                return SafeFuture.completedFuture(
                    InternalValidationResult.ignore("Inclusion List committee mismatch."));
              }
              /*
               * [REJECT] The validator index message.validator_index is within the inclusion_list_committee corresponding to message.inclusion_list_committee_root.
               */
              if (!inclusionListUtil.validatorIndexWithinCommittee(
                  state, slot, inclusionList.getValidatorIndex())) {
                return SafeFuture.completedFuture(
                    InternalValidationResult.reject(
                        "Validator index is not within the inclusion list committee."));
              }

              /*
               * [REJECT] The validator index message.validator_index is within the inclusion_list_committee corresponding to message.inclusion_list_committee_root.
               */
              final UInt64 epoch = spec.computeEpochAtSlot(slot);
              return inclusionListUtil
                  .isValidInclusionListSignature(
                      spec.fork(epoch),
                      state,
                      inclusionList,
                      signedInclusionList.getSignature(),
                      signatureVerifier)
                  .thenApply(
                      isValidInclusionListSignature -> {
                        if (isValidInclusionListSignature) {
                          return InternalValidationResult.ACCEPT;
                        } else {
                          return InternalValidationResult.reject(
                              "Invalid inclusion list signature.");
                        }
                      });
            });
  }

  private int countInclusionLists(
      final NavigableMap<UInt64, ConcurrentMap<UInt64, List<SignedInclusionList>>>
          slotToInclusionListsByValidatorIndex,
      final UInt64 validatorIndex) {
    final Set<Map<UInt64, List<SignedInclusionList>>> validatorInclusionLists =
        slotToInclusionListsByValidatorIndex.values().stream()
            .filter(e -> e.containsKey(validatorIndex))
            .collect(Collectors.toSet());
    return validatorInclusionLists.stream().mapToInt(e -> e.get(validatorIndex).size()).sum();
  }
}
