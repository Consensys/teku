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

package tech.pegasys.teku.statetransition.inclusionlist;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.operations.SignedInclusionList;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.SignedInclusionListValidator;

public class InclusionListManager implements SlotEventsChannel {

  private static final UInt64 SLOTS_TO_RETAIN = UInt64.valueOf(4);

  private final SignedInclusionListValidator signedInclusionListValidator;

  private final NavigableMap<UInt64, Map<UInt64, List<SignedInclusionList>>>
      slotToInclusionListsByValidatorIndex = new TreeMap<>();

  public InclusionListManager(final SignedInclusionListValidator signedInclusionListValidator) {
    this.signedInclusionListValidator = signedInclusionListValidator;
  }

  @Override
  public synchronized void onSlot(final UInt64 slot) {
    slotToInclusionListsByValidatorIndex.headMap(slot.minusMinZero(SLOTS_TO_RETAIN)).clear();
  }

  public void add(final SignedInclusionList signedInclusionList) {
    final UInt64 validatorIndex = signedInclusionList.getMessage().getValidatorIndex();
    final UInt64 slot = signedInclusionList.getMessage().getSlot();
    slotToInclusionListsByValidatorIndex
        .computeIfAbsent(slot, index -> new HashMap<>())
        .compute(
            validatorIndex,
            (index, inclusionLists) -> {
              if (inclusionLists == null) {
                return List.of(signedInclusionList);
              } else {
                List<SignedInclusionList> updatedList = new ArrayList<>(inclusionLists);
                updatedList.add(signedInclusionList);
                return updatedList;
              }
            });
  }

  public SafeFuture<InternalValidationResult> addSignedInclusionList(
      final SignedInclusionList signedInclusionList, final Optional<UInt64> arrivalTimestamp) {
    final SafeFuture<InternalValidationResult> validationResult =
        signedInclusionListValidator.validate(
            signedInclusionList, slotToInclusionListsByValidatorIndex);
    return validationResult.thenApply(
        result -> {
          if (result.isAccept()) {
            add(signedInclusionList);
          }
          return result;
        });
  }

  public List<SignedInclusionList> getInclusionLists(
      final UInt64 slot, final SszBitvector committeeIndices) {
    final Map<UInt64, List<SignedInclusionList>> inclusionListsForSlot =
        slotToInclusionListsByValidatorIndex.getOrDefault(slot, Map.of());
    return inclusionListsForSlot.entrySet().stream()
        .filter(
            validatorIndexToInclusionLists ->
                committeeIndices.isSet(validatorIndexToInclusionLists.getKey().intValue()))
        .flatMap(
            validatorIndexToInclusionLists -> validatorIndexToInclusionLists.getValue().stream())
        .toList();
  }
}
