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

package tech.pegasys.teku.spec.logic.versions.bellatrix.helpers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.PowBlock;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.executionlayer.ExecutionPayloadStatus;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class BellatrixTransitionHelpersTest {

  private final Spec spec = TestSpecFactory.createMinimalBellatrix();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final ExecutionLayerChannel executionLayer = mock(ExecutionLayerChannel.class);
  private final UInt256 terminalDifficulty =
      spec.getGenesisSpecConfig().toVersionBellatrix().orElseThrow().getTerminalTotalDifficulty();

  private UInt64 slot = dataStructureUtil.randomUInt64();

  private final ExecutionPayload payload = dataStructureUtil.randomExecutionPayload();

  @BeforeEach
  void setUp() {
    when(executionLayer.eth1GetPowBlock(any())).thenReturn(completedFuture(Optional.empty()));
  }

  @Test
  void shouldBeSyncingIfPowBlockIsNotAvailable() {
    assertPayloadResultStatus(ExecutionPayloadStatus.SYNCING);
  }

  @Test
  void shouldBeSyncingIfPowParentIsNotAvailable() {
    withPowBlock(payload.getParentHash(), terminalDifficulty);

    assertPayloadResultStatus(ExecutionPayloadStatus.SYNCING);
  }

  @Test
  void shouldBeInvalidIfTotalDifficultyNotReached() {
    withPowBlock(payload.getParentHash(), terminalDifficulty.subtract(1));

    assertPayloadResultStatus(ExecutionPayloadStatus.INVALID);
  }

  @Test
  void shouldBeInvalidIfParentTotalDifficultyIsReached() {
    final PowBlock powBlock = withPowBlock(payload.getParentHash(), terminalDifficulty.plus(1));
    withPowBlock(powBlock.getParentHash(), terminalDifficulty);

    assertPayloadResultStatus(ExecutionPayloadStatus.INVALID);
  }

  @Test
  void shouldBeValidIfTerminalDifficultyConditionsMet() {
    final PowBlock powBlock = withPowBlock(payload.getParentHash(), terminalDifficulty.plus(1));
    withPowBlock(powBlock.getParentHash(), terminalDifficulty.subtract(1));

    assertThat(validateMergeBlock()).isCompletedWithValue(PayloadStatus.VALID);
  }

  @Test
  void shouldBeValidWhenTerminalBlockHashMatchesInActivationEpoch() {
    final UInt64 activationEpoch = UInt64.valueOf(15);
    final Spec spec = createSpec(payload.getParentHash(), activationEpoch);
    final UInt64 blockSlot = spec.computeStartSlotAtEpoch(activationEpoch);
    final SafeFuture<PayloadStatus> result =
        spec.getGenesisSpec()
            .getBellatrixTransitionHelpers()
            .orElseThrow()
            .validateMergeBlock(executionLayer, payload, blockSlot);
    assertPayloadResultStatus(result, ExecutionPayloadStatus.VALID);
  }

  @Test
  void shouldBeValidWhenTerminalBlockHashMatchesAfterActivationEpoch() {
    final UInt64 activationEpoch = UInt64.valueOf(15);
    final Spec spec = createSpec(payload.getParentHash(), activationEpoch);
    final UInt64 blockSlot = spec.computeStartSlotAtEpoch(activationEpoch.plus(1));
    final SafeFuture<PayloadStatus> result =
        spec.getGenesisSpec()
            .getBellatrixTransitionHelpers()
            .orElseThrow()
            .validateMergeBlock(executionLayer, payload, blockSlot);
    assertPayloadResultStatus(result, ExecutionPayloadStatus.VALID);
  }

  @Test
  void shouldBeInvalidWhenTerminalBlockHashDoesNotMatch() {
    final UInt64 activationEpoch = UInt64.valueOf(15);
    final Spec spec = createSpec(dataStructureUtil.randomBytes32(), activationEpoch);
    final UInt64 blockSlot = spec.computeStartSlotAtEpoch(activationEpoch);
    final SafeFuture<PayloadStatus> result =
        spec.getGenesisSpec()
            .getBellatrixTransitionHelpers()
            .orElseThrow()
            .validateMergeBlock(executionLayer, payload, blockSlot);
    assertPayloadResultStatus(result, ExecutionPayloadStatus.INVALID);
  }

  @Test
  void shouldBeInvalidWhenBeforeActivationEpoch() {
    final UInt64 activationEpoch = UInt64.valueOf(15);
    final Spec spec = createSpec(payload.getParentHash(), activationEpoch);
    final UInt64 blockSlot = spec.computeStartSlotAtEpoch(activationEpoch).minus(1);
    final SafeFuture<PayloadStatus> result =
        spec.getGenesisSpec()
            .getBellatrixTransitionHelpers()
            .orElseThrow()
            .validateMergeBlock(executionLayer, payload, blockSlot);
    assertPayloadResultStatus(result, ExecutionPayloadStatus.INVALID);
  }

  private Spec createSpec(
      final Bytes32 terminalBlockHash, final UInt64 terminalBlockHashActivationEpoch) {
    return TestSpecFactory.createMinimalBellatrix(
        c ->
            c.bellatrixBuilder(
                b ->
                    b.terminalBlockHash(terminalBlockHash)
                        .terminalBlockHashActivationEpoch(terminalBlockHashActivationEpoch)));
  }

  private PowBlock withPowBlock(final Bytes32 hash, final UInt256 totalDifficulty) {
    final PowBlock powBlock =
        new PowBlock(hash, dataStructureUtil.randomBytes32(), totalDifficulty, UInt64.ZERO);
    when(executionLayer.eth1GetPowBlock(powBlock.getBlockHash()))
        .thenReturn(completedFuture(Optional.of(powBlock)));
    return powBlock;
  }

  private void assertPayloadResultStatus(final ExecutionPayloadStatus expectedStatus) {
    assertPayloadResultStatus(validateMergeBlock(), expectedStatus);
  }

  private SafeFuture<PayloadStatus> validateMergeBlock() {
    return spec.getGenesisSpec()
        .getBellatrixTransitionHelpers()
        .orElseThrow()
        .validateMergeBlock(executionLayer, payload, slot);
  }

  private void assertPayloadResultStatus(
      final SafeFuture<PayloadStatus> result, final ExecutionPayloadStatus expectedStatus) {
    assertThat(result)
        .isCompletedWithValueMatching(
            payloadResult -> payloadResult.hasStatus(expectedStatus),
            "a payload result with status " + expectedStatus);
  }
}
