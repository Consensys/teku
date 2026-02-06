/*
 * Copyright Consensys Software Inc., 2026
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

import static tech.pegasys.teku.infrastructure.logging.Converter.weiToEth;
import static tech.pegasys.teku.infrastructure.logging.LogFormatter.formatAbbreviatedHashRoot;
import static tech.pegasys.teku.spec.config.SpecConfigGloas.BUILDER_INDEX_SELF_BUILD;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.GetPayloadResponse;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.statetransition.validation.ExecutionPayloadBidGossipValidator;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class DefaultExecutionPayloadBidManager implements ExecutionPayloadBidManager {

  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final ExecutionPayloadBidGossipValidator executionPayloadBidGossipValidator;

  public DefaultExecutionPayloadBidManager(
      final Spec spec,
      final ExecutionPayloadBidGossipValidator executionPayloadBidGossipValidator) {
    this.spec = spec;
    this.executionPayloadBidGossipValidator = executionPayloadBidGossipValidator;
  }

  @Override
  @SuppressWarnings("FutureReturnValueIgnored")
  public SafeFuture<InternalValidationResult> validateAndAddBid(
      final SignedExecutionPayloadBid signedBid, final RemoteBidOrigin remoteBidOrigin) {
    final SafeFuture<InternalValidationResult> validationResult =
        executionPayloadBidGossipValidator.validate(signedBid);
    validationResult.thenAccept(
        result -> {
          switch (result.code()) {
            // TODO-GLOAS handle bids
            case ACCEPT, REJECT, SAVE_FOR_FUTURE, IGNORE -> {}
          }
        });
    return SafeFuture.failedFuture(new UnsupportedOperationException("Not yet implemented"));
  }

  @Override
  public SafeFuture<Optional<SignedExecutionPayloadBid>> getBidForBlock(
      final BeaconState state,
      final SafeFuture<GetPayloadResponse> getPayloadResponseFuture,
      final BlockProductionPerformance blockProductionPerformance) {
    final UInt64 slot = state.getSlot();
    // only local self-built bids for devnet-0
    return getLocalSelfBuiltBid(slot, state, getPayloadResponseFuture).thenApply(Optional::of);
  }

  private SafeFuture<SignedExecutionPayloadBid> getLocalSelfBuiltBid(
      final UInt64 slot,
      final BeaconState state,
      final SafeFuture<GetPayloadResponse> getPayloadResponseFuture) {
    return getPayloadResponseFuture.thenApply(
        getPayloadResponse -> {
          final SignedExecutionPayloadBid localSelfBuiltSignedBid =
              createLocalSelfBuiltSignedBid(getPayloadResponse, slot, state);
          LOG.info(
              "Considering self-built bid (value: {} ETH, EL block: {}) for block at slot {}",
              weiToEth(getPayloadResponse.getExecutionPayloadValue()),
              formatAbbreviatedHashRoot(localSelfBuiltSignedBid.getMessage().getBlockHash()),
              slot);
          return localSelfBuiltSignedBid;
        });
  }

  private SignedExecutionPayloadBid createLocalSelfBuiltSignedBid(
      final GetPayloadResponse getPayloadResponse, final UInt64 slot, final BeaconState state) {
    final SchemaDefinitionsGloas schemaDefinitions =
        SchemaDefinitionsGloas.required(spec.atSlot(slot).getSchemaDefinitions());
    final ExecutionPayload executionPayload = getPayloadResponse.getExecutionPayload();
    final SszList<SszKZGCommitment> blobKzgCommitments =
        schemaDefinitions
            .getBlobKzgCommitmentsSchema()
            .createFromBlobsBundle(getPayloadResponse.getBlobsBundle().orElseThrow());
    // For self-builds, use `BUILDER_INDEX_SELF_BUILD`
    final ExecutionPayloadBid bid =
        schemaDefinitions
            .getExecutionPayloadBidSchema()
            .createLocalSelfBuiltBid(
                BUILDER_INDEX_SELF_BUILD, slot, state, executionPayload, blobKzgCommitments);
    // Using G2_POINT_AT_INFINITY as signature for self-builds
    return schemaDefinitions
        .getSignedExecutionPayloadBidSchema()
        .create(bid, BLSSignature.infinity());
  }
}
