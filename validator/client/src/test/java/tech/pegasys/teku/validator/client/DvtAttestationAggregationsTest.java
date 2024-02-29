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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ethereum.json.types.validator.BeaconCommitteeSelectionProof;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;

class DvtAttestationAggregationsTest {

  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());
  private DvtAttestationAggregations loader;
  private ValidatorApiChannel validatorApiChannel;

  @BeforeEach
  public void setUp() {
    validatorApiChannel = mock(ValidatorApiChannel.class);
  }

  @Test
  public void completesAllFuturesWhenMiddlewareReturnsAllSelectionProofs() {
    when(validatorApiChannel.getBeaconCommitteeSelectionProof(any()))
        .thenReturn(
            SafeFuture.completedFuture(Optional.of(List.of(combinedProof(1), combinedProof(2)))));

    loader = new DvtAttestationAggregations(validatorApiChannel, 2);

    final SafeFuture<BLSSignature> futureSelectionProofValidator1 =
        loader.getCombinedSelectionProofFuture(1, UInt64.ONE, dataStructureUtil.randomSignature());
    final SafeFuture<BLSSignature> futureSelectionProofValidator2 =
        loader.getCombinedSelectionProofFuture(2, UInt64.ONE, dataStructureUtil.randomSignature());

    assertThat(futureSelectionProofValidator1).isCompleted();
    assertThat(futureSelectionProofValidator2).isCompleted();
  }

  @Test
  public void partiallyCompleteFuturesWhenMiddlewareOnlyReturnsSomeSelectionProofs() {
    when(validatorApiChannel.getBeaconCommitteeSelectionProof(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(List.of(combinedProof(1)))));

    loader = new DvtAttestationAggregations(validatorApiChannel, 2);

    final SafeFuture<BLSSignature> futureSelectionProofValidator1 =
        loader.getCombinedSelectionProofFuture(1, UInt64.ONE, dataStructureUtil.randomSignature());
    final SafeFuture<BLSSignature> futureSelectionProofValidator2 =
        loader.getCombinedSelectionProofFuture(2, UInt64.ONE, dataStructureUtil.randomSignature());

    assertThat(futureSelectionProofValidator1).isCompleted();
    assertThat(futureSelectionProofValidator2).isCompletedExceptionally();
  }

  @Test
  public void failAllFuturesIfMiddlewareDoesNotReturnAnyValue() {
    when(validatorApiChannel.getBeaconCommitteeSelectionProof(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    loader = new DvtAttestationAggregations(validatorApiChannel, 3);

    final SafeFuture<BLSSignature> futureSelectionProofValidator1 =
        loader.getCombinedSelectionProofFuture(1, UInt64.ONE, dataStructureUtil.randomSignature());
    final SafeFuture<BLSSignature> futureSelectionProofValidator2 =
        loader.getCombinedSelectionProofFuture(
            2, UInt64.valueOf(2), dataStructureUtil.randomSignature());
    final SafeFuture<BLSSignature> futureSelectionProofValidator3 =
        loader.getCombinedSelectionProofFuture(
            3, UInt64.valueOf(3), dataStructureUtil.randomSignature());

    assertThat(futureSelectionProofValidator1).isCompletedExceptionally();
    assertThat(futureSelectionProofValidator2).isCompletedExceptionally();
    assertThat(futureSelectionProofValidator3).isCompletedExceptionally();
  }

  @Test
  public void handleDifferentValidatorAggregatingInSameSlot() {
    when(validatorApiChannel.getBeaconCommitteeSelectionProof(any()))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(
                    List.of(
                        combinedProofForSlot(1, 1),
                        combinedProofForSlot(2, 1),
                        combinedProofForSlot(3, 1)))));

    loader = new DvtAttestationAggregations(validatorApiChannel, 3);

    final SafeFuture<BLSSignature> futureSelectionProofValidator1 =
        loader.getCombinedSelectionProofFuture(1, UInt64.ONE, dataStructureUtil.randomSignature());
    final SafeFuture<BLSSignature> futureSelectionProofValidator2 =
        loader.getCombinedSelectionProofFuture(2, UInt64.ONE, dataStructureUtil.randomSignature());
    final SafeFuture<BLSSignature> futureSelectionProofValidator3 =
        loader.getCombinedSelectionProofFuture(3, UInt64.ONE, dataStructureUtil.randomSignature());

    assertThat(futureSelectionProofValidator1).isCompleted();
    assertThat(futureSelectionProofValidator2).isCompleted();
    assertThat(futureSelectionProofValidator3).isCompleted();

    // TODO-lucas assert response values map to futures
  }

  @Test
  public void handleSameValidatorAggregatingInDifferentSlots() {
    when(validatorApiChannel.getBeaconCommitteeSelectionProof(any()))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(List.of(combinedProofForSlot(1, 1), combinedProofForSlot(1, 2)))));

    loader = new DvtAttestationAggregations(validatorApiChannel, 2);

    final SafeFuture<BLSSignature> futureSelectionProofValidatorAtSlot1 =
        loader.getCombinedSelectionProofFuture(1, UInt64.ONE, dataStructureUtil.randomSignature());
    final SafeFuture<BLSSignature> futureSelectionProofValidatorAtSlot2 =
        loader.getCombinedSelectionProofFuture(
            1, UInt64.valueOf(2), dataStructureUtil.randomSignature());

    assertThat(futureSelectionProofValidatorAtSlot1).isCompleted();
    assertThat(futureSelectionProofValidatorAtSlot2).isCompleted();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void
      unexpectedErrorHandlingResponseMustCompleteExceptionallyPendingRequestsWithUnderlyingCause() {
    final List<BeaconCommitteeSelectionProof> mockList = mock(List.class);
    // Forcing an unexpected error when handling response
    when(mockList.stream()).thenThrow(new RuntimeException("Unexpected error"));
    when(validatorApiChannel.getBeaconCommitteeSelectionProof(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(mockList)));

    loader = new DvtAttestationAggregations(validatorApiChannel, 1);

    final SafeFuture<BLSSignature> futureSelectionProofValidator1 =
        loader.getCombinedSelectionProofFuture(1, UInt64.ONE, dataStructureUtil.randomSignature());

    assertThat(futureSelectionProofValidator1)
        .isCompletedExceptionally()
        .failsWithin(1, TimeUnit.SECONDS)
        .withThrowableOfType(ExecutionException.class)
        .withCauseInstanceOf(RuntimeException.class)
        .withMessageContaining("Error getting DVT attestation aggregation complete proof");
  }

  @Test
  public void unexpectedErrorHandlingResponseMustCompleteExceptionallyAllNonCompletedRequests() {
    final BeaconCommitteeSelectionProof proofValidator1 = combinedProofForSlot(1, 1);
    final BeaconCommitteeSelectionProof proofValidator2 = spy(combinedProofForSlot(2, 1));
    // Forcing an unexpected error while handling the second proof
    when(proofValidator2.getValidatorIndex()).thenThrow(new RuntimeException("Unexpected error"));
    when(validatorApiChannel.getBeaconCommitteeSelectionProof(any()))
        .thenReturn(
            SafeFuture.completedFuture(Optional.of(List.of(proofValidator1, proofValidator2))));

    loader = new DvtAttestationAggregations(validatorApiChannel, 1);

    final SafeFuture<BLSSignature> futureProofValidator1 =
        loader.getCombinedSelectionProofFuture(1, UInt64.ONE, dataStructureUtil.randomSignature());
    final SafeFuture<BLSSignature> futureProofValidator2 =
        loader.getCombinedSelectionProofFuture(
            2, UInt64.valueOf(2), dataStructureUtil.randomSignature());

    assertThat(futureProofValidator1).isCompleted();
    assertThat(futureProofValidator2).isCompletedExceptionally();
  }

  private BeaconCommitteeSelectionProof combinedProof(final int validatorIndex) {
    return new BeaconCommitteeSelectionProof.Builder()
        .validatorIndex(validatorIndex)
        .slot(UInt64.ONE)
        .selectionProof(dataStructureUtil.randomSignature().toBytesCompressed().toHexString())
        .build();
  }

  private BeaconCommitteeSelectionProof combinedProofForSlot(
      final int validatorIndex, final int slot) {
    return new BeaconCommitteeSelectionProof.Builder()
        .validatorIndex(validatorIndex)
        .slot(UInt64.valueOf(slot))
        .selectionProof(dataStructureUtil.randomSignature().toBytesCompressed().toHexString())
        .build();
  }
}
