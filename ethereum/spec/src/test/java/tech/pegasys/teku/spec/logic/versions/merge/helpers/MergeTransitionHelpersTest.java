/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.spec.logic.versions.merge.helpers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.PowBlock;
import tech.pegasys.teku.spec.executionengine.ExecutePayloadResult;
import tech.pegasys.teku.spec.executionengine.ExecutionEngineChannel;
import tech.pegasys.teku.spec.executionengine.ExecutionPayloadStatus;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class MergeTransitionHelpersTest {

  private final Spec spec = TestSpecFactory.createMinimalMerge();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final ExecutionEngineChannel executionEngine = mock(ExecutionEngineChannel.class);
  private final UInt256 terminalDifficulty =
      spec.getGenesisSpecConfig().toVersionMerge().orElseThrow().getTerminalTotalDifficulty();

  private final ExecutionPayload payload = dataStructureUtil.randomExecutionPayload();

  @BeforeEach
  void setUp() {
    when(executionEngine.getPowBlock(any())).thenReturn(completedFuture(Optional.empty()));
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
  void shouldExecutePayloadIfTerminalDifficultyConditionsMet() {
    final PowBlock powBlock = withPowBlock(payload.getParentHash(), terminalDifficulty.plus(1));
    withPowBlock(powBlock.getParentHash(), terminalDifficulty.subtract(1));

    final ExecutePayloadResult expectedPayloadResult = ExecutePayloadResult.VALID;
    when(executionEngine.executePayload(payload))
        .thenReturn(SafeFuture.completedFuture(expectedPayloadResult));

    assertThat(validateMergeBlock()).isCompletedWithValue(expectedPayloadResult);
    verify(executionEngine).executePayload(payload);
  }

  private PowBlock withPowBlock(final Bytes32 hash, final UInt256 totalDifficulty) {
    final PowBlock powBlock =
        new PowBlock(hash, dataStructureUtil.randomBytes32(), totalDifficulty);
    when(executionEngine.getPowBlock(powBlock.getBlockHash()))
        .thenReturn(completedFuture(Optional.of(powBlock)));
    return powBlock;
  }

  private void assertPayloadResultStatus(final ExecutionPayloadStatus expectedStatus) {
    assertPayloadResultStatus(validateMergeBlock(), expectedStatus);
  }

  private SafeFuture<ExecutePayloadResult> validateMergeBlock() {
    return spec.getGenesisSpec()
        .getMergeTransitionHelpers()
        .orElseThrow()
        .validateMergeBlock(executionEngine, payload);
  }

  private void assertPayloadResultStatus(
      final SafeFuture<ExecutePayloadResult> result, final ExecutionPayloadStatus expectedStatus) {
    assertThat(result)
        .isCompletedWithValueMatching(
            payloadResult -> payloadResult.hasStatus(expectedStatus),
            "a payload result with status " + expectedStatus);
  }
}
