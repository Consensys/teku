/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.statetransition.execution;

import java.util.Optional;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.execution.GetPayloadResponse;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public interface ExecutionPayloadBidManager {

  enum RemoteBidOrigin {
    P2P,
    BUILDER
  }

  ExecutionPayloadBidManager NOOP =
      new ExecutionPayloadBidManager() {

        @Override
        public SafeFuture<InternalValidationResult> validateAndAddBid(
            final SignedExecutionPayloadBid signedBid, final RemoteBidOrigin remoteBidOrigin) {
          return SafeFuture.completedFuture(InternalValidationResult.ACCEPT);
        }

        @Override
        public SafeFuture<Optional<SignedExecutionPayloadBid>> getBidForBlock(
            final BeaconState state,
            final SafeFuture<GetPayloadResponse> getPayloadResponseFuture,
            final BlockProductionPerformance blockProductionPerformance) {
          return SafeFuture.completedFuture(Optional.empty());
        }
      };

  SafeFuture<InternalValidationResult> validateAndAddBid(
      SignedExecutionPayloadBid signedBid, RemoteBidOrigin remoteBidOrigin);

  SafeFuture<Optional<SignedExecutionPayloadBid>> getBidForBlock(
      BeaconState state,
      SafeFuture<GetPayloadResponse> getPayloadResponseFuture,
      BlockProductionPerformance blockProductionPerformance);
}
