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
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.events.ChannelInterface;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.builder.BuilderPayload;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadResult;
import tech.pegasys.teku.spec.datastructures.execution.GetPayloadResponse;
import tech.pegasys.teku.spec.datastructures.execution.HeaderWithFallbackData;
import tech.pegasys.teku.spec.datastructures.execution.NewPayloadRequest;
import tech.pegasys.teku.spec.datastructures.execution.PowBlock;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public interface ExecutionLayerChannel extends ChannelInterface {
  String PREVIOUS_STUB_ENDPOINT_PREFIX = "stub";
  String STUB_ENDPOINT_PREFIX = "unsafe-test-stub";
  ExecutionLayerChannel NOOP =
      new ExecutionLayerChannel() {
        @Override
        public SafeFuture<Optional<PowBlock>> eth1GetPowBlock(final Bytes32 blockHash) {
          return SafeFuture.completedFuture(Optional.empty());
        }

        @Override
        public SafeFuture<PowBlock> eth1GetPowChainHead() {
          throw new UnsupportedOperationException();
        }

        @Override
        public SafeFuture<ForkChoiceUpdatedResult> engineForkChoiceUpdated(
            final ForkChoiceState forkChoiceState,
            final Optional<PayloadBuildingAttributes> payloadBuildingAttributes) {
          return SafeFuture.completedFuture(
              new ForkChoiceUpdatedResult(PayloadStatus.SYNCING, Optional.empty()));
        }

        @Override
        public SafeFuture<GetPayloadResponse> engineGetPayload(
            final ExecutionPayloadContext executionPayloadContext, final UInt64 slot) {
          return SafeFuture.completedFuture(null);
        }

        @Override
        public SafeFuture<PayloadStatus> engineNewPayload(
            final NewPayloadRequest newPayloadRequest) {
          return SafeFuture.completedFuture(PayloadStatus.SYNCING);
        }

        @Override
        public SafeFuture<Void> builderRegisterValidators(
            final SszList<SignedValidatorRegistration> signedValidatorRegistrations,
            final UInt64 slot) {
          return SafeFuture.COMPLETE;
        }

        @Override
        public SafeFuture<BuilderPayload> builderGetPayload(
            final SignedBlockContainer signedBlockContainer,
            final Function<UInt64, Optional<ExecutionPayloadResult>>
                getCachedPayloadResultFunction) {
          return SafeFuture.completedFuture(null);
        }

        @Override
        public SafeFuture<HeaderWithFallbackData> builderGetHeader(
            final ExecutionPayloadContext executionPayloadContext, final BeaconState state) {
          return SafeFuture.completedFuture(null);
        }
      };

  // eth namespace
  SafeFuture<Optional<PowBlock>> eth1GetPowBlock(Bytes32 blockHash);

  SafeFuture<PowBlock> eth1GetPowChainHead();

  // engine namespace
  SafeFuture<ForkChoiceUpdatedResult> engineForkChoiceUpdated(
      ForkChoiceState forkChoiceState,
      Optional<PayloadBuildingAttributes> payloadBuildingAttributes);

  SafeFuture<PayloadStatus> engineNewPayload(NewPayloadRequest newPayloadRequest);

  /**
   * This is low level method, use {@link
   * ExecutionLayerBlockProductionManager#initiateBlockProduction(ExecutionPayloadContext,
   * BeaconState, boolean)} instead
   */
  SafeFuture<GetPayloadResponse> engineGetPayload(
      ExecutionPayloadContext executionPayloadContext, UInt64 slot);

  // builder namespace
  SafeFuture<Void> builderRegisterValidators(
      SszList<SignedValidatorRegistration> signedValidatorRegistrations, UInt64 slot);

  /**
   * This is low level method, use {@link ExecutionLayerBlockProductionManager#getUnblindedPayload(
   * SignedBlockContainer)} instead
   */
  SafeFuture<BuilderPayload> builderGetPayload(
      SignedBlockContainer signedBlockContainer,
      Function<UInt64, Optional<ExecutionPayloadResult>> getCachedPayloadResultFunction);

  /**
   * This is low level method, use {@link
   * ExecutionLayerBlockProductionManager#initiateBlockProduction(ExecutionPayloadContext,
   * BeaconState, boolean)} instead
   */
  SafeFuture<HeaderWithFallbackData> builderGetHeader(
      ExecutionPayloadContext executionPayloadContext, BeaconState state);
}
