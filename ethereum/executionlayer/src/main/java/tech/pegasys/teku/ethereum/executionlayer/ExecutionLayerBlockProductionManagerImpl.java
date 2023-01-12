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

import java.util.Collections;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadResult;
import tech.pegasys.teku.spec.datastructures.execution.HeaderWithFallbackData;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.BlobsBundle;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerBlockProductionManager;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;

public class ExecutionLayerBlockProductionManagerImpl
    implements ExecutionLayerBlockProductionManager, SlotEventsChannel {
  // TODO: Switch to actual Builder API when 4844 version is ready
  protected static final SafeFuture<BlobsBundle> BLOBS_BUNDLE_BUILDER_DUMMY =
      SafeFuture.completedFuture(
          new BlobsBundle(Bytes32.ZERO, Collections.emptyList(), Collections.emptyList()));

  private static final UInt64 EXECUTION_RESULT_CACHE_RETENTION_SLOTS = UInt64.valueOf(2);

  private final NavigableMap<UInt64, ExecutionPayloadResult> executionResultCache =
      new ConcurrentSkipListMap<>();

  private final ExecutionLayerChannel executionLayerChannel;

  public ExecutionLayerBlockProductionManagerImpl(
      final ExecutionLayerChannel executionLayerChannel) {
    this.executionLayerChannel = executionLayerChannel;
  }

  @Override
  public void onSlot(final UInt64 slot) {
    executionResultCache
        .headMap(slot.minusMinZero(EXECUTION_RESULT_CACHE_RETENTION_SLOTS), false)
        .clear();
  }

  @Override
  public Optional<ExecutionPayloadResult> getCachedPayloadResult(final UInt64 slot) {
    return Optional.ofNullable(executionResultCache.get(slot));
  }

  @Override
  public ExecutionPayloadResult initiateBlockProduction(
      final ExecutionPayloadContext context,
      final BeaconState blockSlotState,
      final boolean isBlind) {
    final ExecutionPayloadResult result;
    if (!isBlind) {
      final SafeFuture<ExecutionPayload> executionPayloadFuture =
          executionLayerChannel.engineGetPayload(context, blockSlotState.getSlot());
      result =
          new ExecutionPayloadResult(
              context, Optional.of(executionPayloadFuture), Optional.empty(), Optional.empty());
    } else {
      result = builderGetHeader(context, blockSlotState, false);
    }
    executionResultCache.put(blockSlotState.getSlot(), result);
    return result;
  }

  @Override
  public ExecutionPayloadResult initiateBlockAndBlobsProduction(
      final ExecutionPayloadContext context,
      final BeaconState blockSlotState,
      final boolean isBlind) {
    final ExecutionPayloadResult result;
    if (!isBlind) {
      final SafeFuture<ExecutionPayload> executionPayloadFuture =
          executionLayerChannel.engineGetPayload(context, blockSlotState.getSlot());
      final SafeFuture<BlobsBundle> blobsBundleFuture =
          executionPayloadFuture.thenCompose(
              executionPayload ->
                  executionLayerChannel.engineGetBlobsBundle(
                      blockSlotState.getSlot(),
                      context.getPayloadId(),
                      Optional.of(executionPayload)));
      result =
          new ExecutionPayloadResult(
              context,
              Optional.of(executionPayloadFuture),
              Optional.empty(),
              Optional.of(blobsBundleFuture));
    } else {
      result = builderGetHeader(context, blockSlotState, true);
    }
    executionResultCache.put(blockSlotState.getSlot(), result);
    return result;
  }

  @Override
  public SafeFuture<ExecutionPayload> getUnblindedPayload(
      final SignedBeaconBlock signedBlindedBeaconBlock) {
    return executionLayerChannel.builderGetPayload(
        signedBlindedBeaconBlock, this::getCachedPayloadResult);
  }

  private ExecutionPayloadResult builderGetHeader(
      final ExecutionPayloadContext executionPayloadContext,
      final BeaconState state,
      final boolean post4844) {
    final SafeFuture<HeaderWithFallbackData> executionPayloadHeaderFuture =
        executionLayerChannel.builderGetHeader(executionPayloadContext, state);

    return new ExecutionPayloadResult(
        executionPayloadContext,
        Optional.empty(),
        Optional.of(executionPayloadHeaderFuture),
        post4844 ? Optional.of(BLOBS_BUNDLE_BUILDER_DUMMY) : Optional.empty());
  }
}
