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

package tech.pegasys.teku.spec.executionlayer;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.events.ChannelInterface;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.PowBlock;

public interface ExecutionLayerChannel extends ChannelInterface {
  String STUB_ENDPOINT_IDENTIFIER = "stub";
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
        public SafeFuture<ExecutionPayload> engineGetPayload(
            final ExecutionPayloadContext executionPayloadContext, final UInt64 slot) {
          return SafeFuture.completedFuture(null);
        }

        @Override
        public SafeFuture<PayloadStatus> engineNewPayload(final ExecutionPayload executionPayload) {
          return SafeFuture.completedFuture(PayloadStatus.SYNCING);
        }

        @Override
        public SafeFuture<TransitionConfiguration> engineExchangeTransitionConfiguration(
            TransitionConfiguration transitionConfiguration) {
          return SafeFuture.completedFuture(transitionConfiguration);
        }

        @Override
        public SafeFuture<ExecutionPayloadHeader> builderGetHeader(
            final ExecutionPayloadContext executionPayloadContext, final UInt64 slot) {
          return SafeFuture.completedFuture(null);
        }

        @Override
        public SafeFuture<ExecutionPayload> builderGetPayload(
            SignedBeaconBlock signedBlindedBeaconBlock) {
          return SafeFuture.completedFuture(null);
        }
      };

  // eth namespace
  SafeFuture<Optional<PowBlock>> eth1GetPowBlock(final Bytes32 blockHash);

  SafeFuture<PowBlock> eth1GetPowChainHead();

  // engine namespace
  SafeFuture<ForkChoiceUpdatedResult> engineForkChoiceUpdated(
      final ForkChoiceState forkChoiceState,
      final Optional<PayloadBuildingAttributes> payloadBuildingAttributes);

  SafeFuture<ExecutionPayload> engineGetPayload(
      final ExecutionPayloadContext executionPayloadContext, final UInt64 slot);

  SafeFuture<PayloadStatus> engineNewPayload(final ExecutionPayload executionPayload);

  SafeFuture<TransitionConfiguration> engineExchangeTransitionConfiguration(
      final TransitionConfiguration transitionConfiguration);

  // builder namespace
  SafeFuture<ExecutionPayloadHeader> builderGetHeader(
      final ExecutionPayloadContext executionPayloadContext, final UInt64 slot);

  SafeFuture<ExecutionPayload> builderGetPayload(final SignedBeaconBlock signedBlindedBeaconBlock);

  enum Version {
    KILNV2;

    public static final Version DEFAULT_VERSION = KILNV2;
  }
}
