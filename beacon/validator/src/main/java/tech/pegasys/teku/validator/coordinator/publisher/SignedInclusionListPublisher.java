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

package tech.pegasys.teku.validator.coordinator.publisher;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.networking.eth2.gossip.SignedInclusionListGossipChannel;
import tech.pegasys.teku.spec.datastructures.operations.SignedInclusionList;
import tech.pegasys.teku.statetransition.inclusionlist.InclusionListManager;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class SignedInclusionListPublisher {

  private final InclusionListManager inclusionListManager;
  private final SignedInclusionListGossipChannel signedInclusionListGossipChannel;
  private final TimeProvider timeProvider;

  public SignedInclusionListPublisher(
      final InclusionListManager inclusionListManager,
      final SignedInclusionListGossipChannel signedInclusionListGossipChannel,
      final TimeProvider timeProvider) {
    this.inclusionListManager = inclusionListManager;
    this.signedInclusionListGossipChannel = signedInclusionListGossipChannel;
    this.timeProvider = timeProvider;
  }

  public SafeFuture<InternalValidationResult> sendInclusionList(
      final SignedInclusionList signedInclusionList) {
    publishSignedInclusionList(signedInclusionList);
    return inclusionListManager.addSignedInclusionList(
        signedInclusionList, Optional.of(timeProvider.getTimeInMillis()));
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  private void publishSignedInclusionList(final SignedInclusionList signedInclusionList) {
    signedInclusionListGossipChannel.publishInclusionList(signedInclusionList);
  }
}
