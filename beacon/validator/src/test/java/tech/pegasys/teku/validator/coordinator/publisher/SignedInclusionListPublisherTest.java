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

package tech.pegasys.teku.validator.coordinator.publisher;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.SignedInclusionListGossipChannel;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.SignedInclusionList;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.inclusionlist.InclusionListManager;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

class SignedInclusionListPublisherTest {

  private final Spec spec = TestSpecFactory.createMinimalHeze();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final InclusionListManager inclusionListManager = mock(InclusionListManager.class);
  private final SignedInclusionListGossipChannel signedInclusionListGossipChannel =
      mock(SignedInclusionListGossipChannel.class);
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInMillis(1234);

  private final SignedInclusionListPublisher signedInclusionListPublisher =
      new SignedInclusionListPublisher(
          inclusionListManager, signedInclusionListGossipChannel, timeProvider);

  private final SignedInclusionList signedInclusionList =
      dataStructureUtil.randomSignedInclusionList();

  @Test
  void sendInclusionListShouldPublishAfterValidationAccepts() {
    final SafeFuture<InternalValidationResult> validationResult = new SafeFuture<>();
    when(inclusionListManager.addSignedInclusionList(
            signedInclusionList, Optional.of(timeProvider.getTimeInMillis())))
        .thenReturn(validationResult);

    final SafeFuture<InternalValidationResult> result =
        signedInclusionListPublisher.sendInclusionList(signedInclusionList);

    verify(inclusionListManager)
        .addSignedInclusionList(signedInclusionList, Optional.of(UInt64.valueOf(1234)));
    verifyNoInteractions(signedInclusionListGossipChannel);

    validationResult.complete(InternalValidationResult.ACCEPT);

    assertThatSafeFuture(result).isCompletedWithValue(InternalValidationResult.ACCEPT);
    verify(signedInclusionListGossipChannel).publishInclusionList(signedInclusionList);
  }

  @Test
  void sendInclusionListShouldNotPublishRejectedInclusionList() {
    final InternalValidationResult rejected = InternalValidationResult.reject("invalid");
    when(inclusionListManager.addSignedInclusionList(
            signedInclusionList, Optional.of(timeProvider.getTimeInMillis())))
        .thenReturn(SafeFuture.completedFuture(rejected));

    assertThatSafeFuture(signedInclusionListPublisher.sendInclusionList(signedInclusionList))
        .isCompletedWithValue(rejected);

    verifyNoInteractions(signedInclusionListGossipChannel);
  }

  @Test
  void sendInclusionListShouldNotPublishInclusionListSavedForFuture() {
    when(inclusionListManager.addSignedInclusionList(
            signedInclusionList, Optional.of(timeProvider.getTimeInMillis())))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.SAVE_FOR_FUTURE));

    assertThatSafeFuture(signedInclusionListPublisher.sendInclusionList(signedInclusionList))
        .isCompletedWithValue(InternalValidationResult.SAVE_FOR_FUTURE);

    verifyNoInteractions(signedInclusionListGossipChannel);
  }
}
