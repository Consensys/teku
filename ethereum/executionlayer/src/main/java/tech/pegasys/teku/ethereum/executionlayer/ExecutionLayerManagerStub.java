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

package tech.pegasys.teku.ethereum.executionlayer;

import java.util.Optional;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadResult;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannelStub;

public class ExecutionLayerManagerStub extends ExecutionLayerChannelStub
    implements ExecutionLayerManager {
  private static final Logger LOG = LogManager.getLogger();

  private final BuilderCircuitBreaker builderCircuitBreaker;

  public ExecutionLayerManagerStub(
      Spec spec,
      TimeProvider timeProvider,
      boolean enableTransitionEmulation,
      final Optional<Bytes32> terminalBlockHashInTTDMode,
      final BuilderCircuitBreaker builderCircuitBreaker) {
    super(spec, timeProvider, enableTransitionEmulation, terminalBlockHashInTTDMode);
    this.builderCircuitBreaker = builderCircuitBreaker;
  }

  @Override
  public void onSlot(UInt64 slot) {
    // NOOP
  }

  @Override
  public SafeFuture<ExecutionPayload> builderGetPayload(
      SignedBeaconBlock signedBlindedBeaconBlock,
      Function<UInt64, Optional<ExecutionPayloadResult>> getPayloadResultFunction) {
    // FIXME: no sense
    LOG.debug("Block {}, circuit breaker {}", signedBlindedBeaconBlock, builderCircuitBreaker);
    return SafeFuture.completedFuture(null);
  }
}
