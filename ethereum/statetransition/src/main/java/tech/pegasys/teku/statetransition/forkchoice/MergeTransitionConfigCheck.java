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

import java.time.Duration;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.Cancellable;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.config.SpecConfigBellatrix;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.executionlayer.TransitionConfiguration;

public class MergeTransitionConfigCheck extends Service {

  /**
   * From the execution engine spec:
   *
   * <blockquote>
   *
   * Consensus Layer client software SHOULD poll this endpoint every 60 seconds
   *
   * </blockquote>
   */
  private static final Duration POLLING_PERIOD = Duration.ofSeconds(60);

  private static final Logger LOG = LogManager.getLogger();

  private final EventLogger eventLogger;
  private final Spec spec;
  private final ExecutionLayerChannel executionLayer;
  private final AsyncRunner asyncRunner;

  private Optional<Cancellable> timer = Optional.empty();
  private TransitionConfiguration localTransitionConfiguration;

  public MergeTransitionConfigCheck(
      final EventLogger eventLogger,
      final Spec spec,
      final ExecutionLayerChannel executionLayer,
      final AsyncRunner asyncRunner) {
    this.eventLogger = eventLogger;
    this.spec = spec;
    this.executionLayer = executionLayer;
    this.asyncRunner = asyncRunner;
  }

  @Override
  protected synchronized SafeFuture<?> doStart() {
    final SpecVersion bellatrixMilestone = spec.forMilestone(SpecMilestone.BELLATRIX);
    if (bellatrixMilestone == null) {
      LOG.debug("Bellatrix is not scheduled. Not checking transition configuration.");
      return SafeFuture.COMPLETE;
    }
    final SpecConfigBellatrix specConfigBellatrix =
        bellatrixMilestone.getConfig().toVersionBellatrix().orElseThrow();

    localTransitionConfiguration =
        new TransitionConfiguration(
            specConfigBellatrix.getTerminalTotalDifficulty(),
            specConfigBellatrix.getTerminalBlockHash(),
            UInt64.ZERO);

    timer =
        Optional.of(
            asyncRunner.runWithFixedDelay(
                this::verifyTransitionConfiguration,
                POLLING_PERIOD,
                (error) -> LOG.error("An error occurred while executing the monitor task", error)));
    return SafeFuture.COMPLETE;
  }

  @Override
  protected synchronized SafeFuture<?> doStop() {
    timer.ifPresent(Cancellable::cancel);
    timer = Optional.empty();
    return SafeFuture.COMPLETE;
  }

  private synchronized void verifyTransitionConfiguration() {
    executionLayer
        .engineExchangeTransitionConfiguration(localTransitionConfiguration)
        .thenAccept(
            remoteTransitionConfiguration -> {
              if (!localTransitionConfiguration
                      .getTerminalTotalDifficulty()
                      .equals(remoteTransitionConfiguration.getTerminalTotalDifficulty())
                  || !localTransitionConfiguration
                      .getTerminalBlockHash()
                      .equals(remoteTransitionConfiguration.getTerminalBlockHash())) {

                eventLogger.transitionConfigurationTtdTbhMismatch(
                    localTransitionConfiguration.toString(),
                    remoteTransitionConfiguration.toString());
              } else if (remoteTransitionConfiguration.getTerminalBlockHash().isZero()
                  && !remoteTransitionConfiguration.getTerminalBlockNumber().isZero()) {

                eventLogger.transitionConfigurationRemoteTbhTbnInconsistency(
                    remoteTransitionConfiguration.toString());
              }
            })
        .finish(
            error ->
                LOG.error(
                    "An error occurred while querying remote transition configuration", error));
  }
}
