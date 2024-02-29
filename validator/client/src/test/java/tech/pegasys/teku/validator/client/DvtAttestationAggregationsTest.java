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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
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
  public void completesAllFuturesWhenMiddlewareReturnsAllCombinedSelectionProofs() {
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

    verify(validatorApiChannel, times(1)).getBeaconCommitteeSelectionProof(any());
  }

  @Test
  public void partiallyCompleteFuturesWhenMiddlewareOnlyReturnsSomeCombinedSelectionProofs() {
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
        loader.getCombinedSelectionProofFuture(2, UInt64.ONE, dataStructureUtil.randomSignature());
    final SafeFuture<BLSSignature> futureSelectionProofValidator3 =
        loader.getCombinedSelectionProofFuture(3, UInt64.ONE, dataStructureUtil.randomSignature());

    assertThat(futureSelectionProofValidator1).isCompletedExceptionally();
    assertThat(futureSelectionProofValidator2).isCompletedExceptionally();
    assertThat(futureSelectionProofValidator3).isCompletedExceptionally();
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
