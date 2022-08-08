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
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.events.ChannelInterface;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.PowBlock;
import tech.pegasys.teku.spec.datastructures.execution.SignedValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public interface ExecutionLayerChannel extends ChannelInterface {
  String STUB_ENDPOINT_PREFIX = "stub";
  ExecutionLayerChannel NOOP =
      new ExecutionLayerChannel() {
        // FIXME will be calling this to get payload when its not stored
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
            final TransitionConfiguration transitionConfiguration) {
          return SafeFuture.completedFuture(transitionConfiguration);
        }

        @Override
        public SafeFuture<Void> builderRegisterValidators(
            final SszList<SignedValidatorRegistration> signedValidatorRegistrations,
            final UInt64 slot) {
          return SafeFuture.COMPLETE;
        }

        @Override
        public SafeFuture<ExecutionPayloadHeader> builderGetHeader(
            final ExecutionPayloadContext executionPayloadContext,
            final BeaconState state,
            final boolean transitionNotFinalized) {
          return SafeFuture.completedFuture(null);
        }

        @Override
        public SafeFuture<ExecutionPayload> builderGetPayload(
            SignedBeaconBlock signedBlindedBeaconBlock) {
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

  SafeFuture<ExecutionPayload> engineGetPayload(
      ExecutionPayloadContext executionPayloadContext, UInt64 slot);

  SafeFuture<PayloadStatus> engineNewPayload(ExecutionPayload executionPayload);

  SafeFuture<TransitionConfiguration> engineExchangeTransitionConfiguration(
      TransitionConfiguration transitionConfiguration);

  // builder namespace
  SafeFuture<Void> builderRegisterValidators(
      SszList<SignedValidatorRegistration> signedValidatorRegistrations, UInt64 slot);

  SafeFuture<ExecutionPayloadHeader> builderGetHeader(
      ExecutionPayloadContext executionPayloadContext,
      BeaconState state,
      boolean transitionNotFinalized);

  SafeFuture<ExecutionPayload> builderGetPayload(SignedBeaconBlock signedBlindedBeaconBlock);

  enum Version {
    KILNV2;

    public static final Version DEFAULT_VERSION = KILNV2;
  }
}
