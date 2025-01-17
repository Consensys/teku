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

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.operations.SignedInclusionList;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

// TODO EIP7805 implement pool logic
public class InclusionListPool implements SlotEventsChannel {

  @Override
  public synchronized void onSlot(final UInt64 slot) {}

  public void add(final SignedInclusionList signedInclusionList) {}

  public SafeFuture<InternalValidationResult> addRemote(
      final SignedInclusionList signedInclusionList, final Optional<UInt64> arrivalTimestamp) {
    return SafeFuture.completedFuture(InternalValidationResult.ACCEPT);
  }

  public List<SignedInclusionList> getInclusionLists(final UInt64 slot) {
    return Collections.emptyList();
  }
}
