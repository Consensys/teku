/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.statetransition.forkchoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigBuilder.BellatrixBuilder;
import tech.pegasys.teku.spec.config.SpecConfigLoader;
import tech.pegasys.teku.spec.executionengine.ExecutionEngineChannel;
import tech.pegasys.teku.spec.executionengine.TransitionConfiguration;
import tech.pegasys.teku.spec.logic.common.block.AbstractBlockProcessor;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class MergeTransitionConfigCheckTest {

  private static final UInt64 BELLATRIX_FORK_EPOCH = UInt64.ONE;
  private static final Bytes32 TERMINAL_BLOCK_HASH = Bytes32.random();
  private static final UInt64 TERMINAL_BLOCK_EPOCH = UInt64.valueOf(2);

  private final ExecutionEngineChannel executionEngine = mock(ExecutionEngineChannel.class);
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(10_000);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);
  private final EventLogger eventLogger = Mockito.mock(EventLogger.class);

  private DataStructureUtil dataStructureUtil;
  private MergeTransitionConfigCheck mergeTransitionConfigCheck;
  private TransitionConfiguration localTransitionConfiguration;

  @BeforeAll
  public static void initSession() {
    AbstractBlockProcessor.blsVerifyDeposit = false;
  }

  @AfterAll
  public static void resetSession() {
    AbstractBlockProcessor.blsVerifyDeposit = true;
  }

  @Test
  void shouldDetectTerminalConfigProblems() {
    setUpTerminalBlockHashConfig();
    final UInt256 wrongRemoteTTD = dataStructureUtil.randomUInt256();
    final Bytes32 wrongRemoteTBH = dataStructureUtil.randomBytes32();

    TransitionConfiguration wrongRemoteConfig;

    assertThat(mergeTransitionConfigCheck.start()).isCompleted();

    // wrong terminal total difficulty
    wrongRemoteConfig =
        new TransitionConfiguration(
            wrongRemoteTTD, localTransitionConfiguration.getTerminalBlockHash(), UInt64.ZERO);
    when(executionEngine.exchangeTransitionConfiguration(localTransitionConfiguration))
        .thenReturn(SafeFuture.completedFuture(wrongRemoteConfig));

    asyncRunner.executeQueuedActions();

    verify(eventLogger)
        .transitionConfigurationTtdTbhMismatch(
            localTransitionConfiguration.toString(), wrongRemoteConfig.toString());

    // wrong terminal block hash
    wrongRemoteConfig =
        new TransitionConfiguration(
            localTransitionConfiguration.getTerminalTotalDifficulty(), wrongRemoteTBH, UInt64.ZERO);
    when(executionEngine.exchangeTransitionConfiguration(localTransitionConfiguration))
        .thenReturn(SafeFuture.completedFuture(wrongRemoteConfig));

    asyncRunner.executeQueuedActions();

    verify(eventLogger)
        .transitionConfigurationTtdTbhMismatch(
            localTransitionConfiguration.toString(), wrongRemoteConfig.toString());

    // remote terminal block hash / terminal block number inconsistency
    wrongRemoteConfig =
        new TransitionConfiguration(
            localTransitionConfiguration.getTerminalTotalDifficulty(),
            localTransitionConfiguration.getTerminalBlockHash(),
            UInt64.ZERO);
    when(executionEngine.exchangeTransitionConfiguration(localTransitionConfiguration))
        .thenReturn(SafeFuture.completedFuture(wrongRemoteConfig));

    asyncRunner.executeQueuedActions();

    verify(eventLogger)
        .transitionConfigurationRemoteTbhTbnInconsistency(wrongRemoteConfig.toString());

    verifyNoMoreInteractions(eventLogger);
  }

  private void setUpTerminalBlockHashConfig() {
    setUpCommon(
        bellatrixBuilder ->
            bellatrixBuilder
                .bellatrixForkEpoch(BELLATRIX_FORK_EPOCH)
                .terminalBlockHash(TERMINAL_BLOCK_HASH)
                .terminalBlockHashActivationEpoch(TERMINAL_BLOCK_EPOCH));

    when(executionEngine.exchangeTransitionConfiguration(localTransitionConfiguration))
        .thenReturn(
            SafeFuture.completedFuture(
                new TransitionConfiguration(
                    localTransitionConfiguration.getTerminalTotalDifficulty(),
                    localTransitionConfiguration.getTerminalBlockHash(),
                    dataStructureUtil.randomUInt64())));
  }

  private void setUpCommon(Consumer<BellatrixBuilder> bellatrixBuilder) {
    Spec spec =
        TestSpecFactory.createBellatrix(
            SpecConfigLoader.loadConfig(
                "minimal",
                phase0Builder ->
                    phase0Builder
                        .altairBuilder(altairBuilder -> altairBuilder.altairForkEpoch(UInt64.ZERO))
                        .bellatrixBuilder(bellatrixBuilder)));
    dataStructureUtil = new DataStructureUtil(spec);

    localTransitionConfiguration =
        new TransitionConfiguration(
            spec.getGenesisSpecConfig()
                .toVersionBellatrix()
                .orElseThrow()
                .getTerminalTotalDifficulty(),
            spec.getGenesisSpecConfig().toVersionBellatrix().orElseThrow().getTerminalBlockHash(),
            UInt64.ZERO);

    mergeTransitionConfigCheck =
        new MergeTransitionConfigCheck(eventLogger, spec, executionEngine, asyncRunner);
  }
}
