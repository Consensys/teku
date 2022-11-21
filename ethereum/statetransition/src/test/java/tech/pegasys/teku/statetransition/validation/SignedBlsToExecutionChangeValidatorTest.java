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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.operations.BlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.RecentChainData;

class SignedBlsToExecutionChangeValidatorTest {

  private final Spec spec = spy(TestSpecFactory.createMinimalCapella());
  private final RecentChainData recentChainData = mock(RecentChainData.class);

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private SignedBlsToExecutionChangeValidator validator;

  @BeforeEach
  public void beforeEach() {
    Mockito.reset(spec, recentChainData);
    when(recentChainData.getBestState())
        .thenReturn(Optional.of(SafeFuture.completedFuture(mock(BeaconState.class))));
    validator = new SignedBlsToExecutionChangeValidator(spec, recentChainData);
  }

  @Test
  public void validateFullyShouldAcceptValidMessage() {
    final SignedBlsToExecutionChange message = dataStructureUtil.randomSignedBlsToExecutionChange();
    mockSpecValidationSucceeded();
    mockSignatureVerificationSucceeded();

    final SafeFuture<InternalValidationResult> validationResult =
        validator.validateForGossip(message);

    assertValidationResult(validationResult, ValidationResultCode.ACCEPT);
  }

  @Test
  public void validateFullyShouldIgnoreSubsequentMessagesForSameValidator() {
    final SignedBlsToExecutionChange message = dataStructureUtil.randomSignedBlsToExecutionChange();
    mockSpecValidationSucceeded();
    mockSignatureVerificationSucceeded();

    final SafeFuture<InternalValidationResult> firstValidationResult =
        validator.validateForGossip(message);
    final SafeFuture<InternalValidationResult> secondValidationResult =
        validator.validateForGossip(message);

    assertValidationResult(firstValidationResult, ValidationResultCode.ACCEPT);
    assertValidationResult(
        secondValidationResult,
        ValidationResultCode.IGNORE,
        "BlsToExecutionChange is not the first one for validator");
  }

  @Test
  public void validateFullyShouldRejectMessageIfSpecValidationFails() {
    final SignedBlsToExecutionChange signedBlsToExecutionChange =
        dataStructureUtil.randomSignedBlsToExecutionChange();
    final String expectedFailureDescription = "Spec validation failed";
    mockSpecValidationFailed(expectedFailureDescription);

    final SafeFuture<InternalValidationResult> validationResult =
        validator.validateForGossip(signedBlsToExecutionChange);

    assertValidationResult(
        validationResult, ValidationResultCode.REJECT, expectedFailureDescription);
  }

  @Test
  public void validateForBlockInclusionShouldReturnSpecValidationInvalidReasonWhenInvalid() {
    final SignedBlsToExecutionChange signedBlsToExecutionChange =
        dataStructureUtil.randomSignedBlsToExecutionChange();
    final String expectedFailureDescription = "Spec validation failed";
    mockSpecValidationFailed(expectedFailureDescription);

    final Optional<OperationInvalidReason> maybeOperationInvalidReason =
        validator.validateForBlockInclusion(mock(BeaconState.class), signedBlsToExecutionChange);

    assertThat(maybeOperationInvalidReason).isNotEmpty();
    assertThat(maybeOperationInvalidReason.get().describe()).contains(expectedFailureDescription);
  }

  @Test
  public void validateForBlockInclusionShouldReturnEmptyIfSpecValidationSucceeds() {
    final SignedBlsToExecutionChange signedBlsToExecutionChange =
        dataStructureUtil.randomSignedBlsToExecutionChange();
    mockSpecValidationSucceeded();
    mockSignatureVerificationSucceeded();

    final Optional<OperationInvalidReason> maybeOperationInvalidReason =
        validator.validateForBlockInclusion(mock(BeaconState.class), signedBlsToExecutionChange);

    assertThat(maybeOperationInvalidReason).isEmpty();
  }

  private void assertValidationResult(
      final SafeFuture<InternalValidationResult> validationResult,
      final ValidationResultCode expectedResultCode) {
    assertThat(validationResult)
        .isCompletedWithValueMatching(result -> result.code() == expectedResultCode);
  }

  private void assertValidationResult(
      final SafeFuture<InternalValidationResult> validationResult,
      final ValidationResultCode expectedResultCode,
      final String expectedDescription) {
    assertThat(validationResult)
        .isCompletedWithValueMatching(
            result ->
                result.code() == expectedResultCode
                    && result.getDescription().orElseThrow().contains(expectedDescription));
  }

  private void mockSpecValidationSucceeded() {
    doReturn(Optional.empty())
        .when(spec)
        .validateBlsToExecutionChange(any(BeaconState.class), any(BlsToExecutionChange.class));
  }

  private void mockSpecValidationFailed(final String expectedDescription) {
    final OperationInvalidReason expectedInvalidReason = () -> expectedDescription;
    doReturn(Optional.of(expectedInvalidReason))
        .when(spec)
        .validateBlsToExecutionChange(any(BeaconState.class), any(BlsToExecutionChange.class));
  }

  private void mockSignatureVerificationSucceeded() {
    doReturn(true)
        .when(spec)
        .verifyBlsToExecutionChangeSignature(
            any(BeaconState.class),
            any(SignedBlsToExecutionChange.class),
            eq(BLSSignatureVerifier.SIMPLE));
  }
}
