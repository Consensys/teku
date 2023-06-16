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

package tech.pegasys.teku.spec.executionlayer;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.builder.BuilderPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadResult;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

/**
 * Designed to handle specification defined ordering and caching of block production operations.
 *
 * <p>Always use this manager instead of using {@link ExecutionLayerChannel} directly for block
 * production-related activities
 */
public interface ExecutionLayerBlockProductionManager {
  ExecutionLayerBlockProductionManager NOOP =
      new ExecutionLayerBlockProductionManager() {
        @Override
        public Optional<ExecutionPayloadResult> getCachedPayloadResult(final UInt64 slot) {
          return Optional.empty();
        }

        @Override
        public ExecutionPayloadResult initiateBlockProduction(
            final ExecutionPayloadContext context,
            final BeaconState blockSlotState,
            final boolean isBlind) {
          return null;
        }

        @Override
        public ExecutionPayloadResult initiateBlockAndBlobsProduction(
            final ExecutionPayloadContext context,
            final BeaconState blockSlotState,
            final boolean isBlind) {
          return null;
        }

        @Override
        public SafeFuture<BuilderPayload> getUnblindedPayload(
            final SignedBlockContainer signedBlockContainer) {
          return SafeFuture.completedFuture(null);
        }

        @Override
        public Optional<BuilderPayload> getCachedUnblindedPayload(final UInt64 slot) {
          return Optional.empty();
        }
      };

  /**
   * Initiates block production flow with execution client or builder
   *
   * @param context Payload context
   * @param blockSlotState pre state
   * @param isBlind Block type. Use blind for builder building
   * @return Container with filled Payload or Payload Header futures
   */
  ExecutionPayloadResult initiateBlockProduction(
      ExecutionPayloadContext context, BeaconState blockSlotState, boolean isBlind);

  /**
   * Initiates block and sidecar blobs production flow with execution client or builder. Use since
   * Deneb.
   *
   * @param context Payload context
   * @param blockSlotState pre state
   * @param isBlind Block type. Use blind for builder building
   * @return Container with filled Payload or Payload Header futures
   */
  ExecutionPayloadResult initiateBlockAndBlobsProduction(
      ExecutionPayloadContext context, BeaconState blockSlotState, boolean isBlind);

  Optional<ExecutionPayloadResult> getCachedPayloadResult(UInt64 slot);

  SafeFuture<BuilderPayload> getUnblindedPayload(SignedBlockContainer signedBlockContainer);

  /**
   * Requires {@link #getUnblindedPayload( SignedBlockContainer)} to have been called first in order
   * for a value to be present
   */
  @SuppressWarnings("unused")
  Optional<BuilderPayload> getCachedUnblindedPayload(UInt64 slot);
}
