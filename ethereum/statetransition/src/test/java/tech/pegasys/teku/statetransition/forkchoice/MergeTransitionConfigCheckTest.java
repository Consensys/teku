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

package tech.pegasys.teku.statetransition.forkchoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigLoader;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.executionlayer.TransitionConfiguration;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class MergeTransitionConfigCheckTest {

  private static final UInt64 BELLATRIX_FORK_EPOCH = UInt64.ONE;
  private static final Bytes32 TERMINAL_BLOCK_HASH = Bytes32.random();
  private static final UInt64 TERMINAL_BLOCK_EPOCH = UInt64.valueOf(2);

  private final ExecutionLayerChannel executionLayer = mock(ExecutionLayerChannel.class);
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(10_000);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);
  private final EventLogger eventLogger = Mockito.mock(EventLogger.class);

  private final Spec spec =
      TestSpecFactory.createBellatrix(
          SpecConfigLoader.loadConfig(
              "minimal",
              phase0Builder ->
                  phase0Builder
                      .altairBuilder(altairBuilder -> altairBuilder.altairForkEpoch(UInt64.ZERO))
                      .bellatrixBuilder(
                          bellatrixBuilder ->
                              bellatrixBuilder
                                  .bellatrixForkEpoch(BELLATRIX_FORK_EPOCH)
                                  .terminalBlockHash(TERMINAL_BLOCK_HASH)
                                  .terminalBlockHashActivationEpoch(TERMINAL_BLOCK_EPOCH))));

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private TransitionConfiguration localTransitionConfiguration;
  private TransitionConfiguration remoteTransitionConfiguration;

  private final UInt256 wrongRemoteTTD = dataStructureUtil.randomUInt256();
  private final Bytes32 wrongRemoteTBH = dataStructureUtil.randomBytes32();

  @BeforeEach
  void setUp() {
    localTransitionConfiguration =
        new TransitionConfiguration(
            spec.getGenesisSpecConfig()
                .toVersionBellatrix()
                .orElseThrow()
                .getTerminalTotalDifficulty(),
            spec.getGenesisSpecConfig().toVersionBellatrix().orElseThrow().getTerminalBlockHash(),
            UInt64.ZERO);
    final MergeTransitionConfigCheck mergeTransitionConfigCheck =
        new MergeTransitionConfigCheck(eventLogger, spec, executionLayer, asyncRunner);
    setRemoteTransitionConfiguration(
        new TransitionConfiguration(
            localTransitionConfiguration.getTerminalTotalDifficulty(),
            localTransitionConfiguration.getTerminalBlockHash(),
            dataStructureUtil.randomUInt64()));

    assertThat(mergeTransitionConfigCheck.start()).isCompleted();
  }

  @Test
  void shouldReportWrongBlockNumber() {
    // already in setUp
    asyncRunner.executeQueuedActions();

    verify(eventLogger)
        .transitionConfigurationTtdTbhMismatch(
            localTransitionConfiguration.toString(), remoteTransitionConfiguration.toString());
  }

  @Test
  void shouldReportWrongTotalTerminalDifficulty() {
    setRemoteTransitionConfiguration(
        new TransitionConfiguration(
            wrongRemoteTTD, localTransitionConfiguration.getTerminalBlockHash(), UInt64.ZERO));

    asyncRunner.executeQueuedActions();

    verify(eventLogger)
        .transitionConfigurationTtdTbhMismatch(
            localTransitionConfiguration.toString(), remoteTransitionConfiguration.toString());
  }

  @Test
  void shouldDetectWrongTerminalBlockHash() {
    setRemoteTransitionConfiguration(
        new TransitionConfiguration(
            localTransitionConfiguration.getTerminalTotalDifficulty(),
            wrongRemoteTBH,
            UInt64.ZERO));

    asyncRunner.executeQueuedActions();

    verify(eventLogger)
        .transitionConfigurationTtdTbhMismatch(
            localTransitionConfiguration.toString(), remoteTransitionConfiguration.toString());
  }

  @Test
  void shouldPassWithEqualConfiguration() {
    setRemoteTransitionConfiguration(
        new TransitionConfiguration(
            localTransitionConfiguration.getTerminalTotalDifficulty(),
            localTransitionConfiguration.getTerminalBlockHash(),
            localTransitionConfiguration.getTerminalBlockNumber()));

    asyncRunner.executeQueuedActions();
    verifyNoMoreInteractions(eventLogger);
  }

  private void setRemoteTransitionConfiguration(
      final TransitionConfiguration transitionConfiguration) {
    this.remoteTransitionConfiguration = transitionConfiguration;
    when(executionLayer.engineExchangeTransitionConfiguration(localTransitionConfiguration))
        .thenReturn(SafeFuture.completedFuture(remoteTransitionConfiguration));
  }
}
