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

package tech.pegasys.teku.statetransition.lightclient;

import java.util.Optional;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientFinalityUpdate;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientOptimisticUpdate;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientUpdate;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.util.LightClientUtil;
import tech.pegasys.teku.statetransition.block.ReceivedBlockEventsChannel;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class LightClientServerService
    implements ReceivedBlockEventsChannel, FinalizedCheckpointChannel {

  private static final Logger LOG = LogManager.getLogger();

  /** Sync committee periods of updates retained behind the finalized period. */
  private static final int MAX_RETAINED_PERIODS = 128;

  private final Spec spec;
  private final LightClientUpdateStore lightClientStore;
  private final Function<Bytes32, SafeFuture<Optional<SignedBeaconBlock>>> retrieveBlockByRoot;
  private final Function<Bytes32, SafeFuture<Optional<BeaconState>>> retrieveStateByRoot;

  public LightClientServerService(
      final Spec spec,
      final LightClientUpdateStore lightClientStore,
      final CombinedChainDataClient combinedChainDataClient) {
    this(
        spec,
        lightClientStore,
        combinedChainDataClient::getBlockByBlockRoot,
        combinedChainDataClient::getStateByBlockRoot);
  }

  public LightClientServerService(
      final Spec spec,
      final LightClientUpdateStore lightClientStore,
      final Function<Bytes32, SafeFuture<Optional<SignedBeaconBlock>>> retrieveBlockByRoot,
      final Function<Bytes32, SafeFuture<Optional<BeaconState>>> retrieveStateByRoot) {
    this.spec = spec;
    this.lightClientStore = lightClientStore;
    this.retrieveBlockByRoot = retrieveBlockByRoot;
    this.retrieveStateByRoot = retrieveStateByRoot;
  }

  @Override
  public void onBlockValidated(final SignedBeaconBlock block) {
    // This is a no-operation because we cannot generate any light client updates until the block is
    // imported
  }

  @Override
  public void onBlockImported(final SignedBeaconBlock block, final boolean isExecutionOptimistic) {
    if (isExecutionOptimistic) {
      return;
    }

    final UInt64 slot = block.getSlot();

    if (spec.atSlot(slot).getMilestone().isLessThan(SpecMilestone.ALTAIR)) {
      return;
    }

    final Optional<SyncAggregate> maybeSyncAggregate =
        block.getMessage().getBody().getOptionalSyncAggregate();

    if (maybeSyncAggregate.isEmpty()) {
      return;
    }

    if (maybeSyncAggregate.get().getSyncCommitteeBits().getBitCount()
        < SpecConfigAltair.required(spec.atSlot(slot).getConfig())
            .getMinSyncCommitteeParticipants()) {
      return;
    }

    retrieveStateByRoot
        .apply(block.getRoot())
        .thenCompose(
            maybePostState ->
                maybePostState
                    .map(postState -> createAndStoreUpdate(block, postState))
                    .orElseGet(() -> SafeFuture.completedFuture(Optional.empty())))
        .finish(
            error ->
                LOG.warn(
                    "Failed to create light client update for block {}", block.getRoot(), error));
  }

  @Override
  public void onNewFinalizedCheckpoint(
      final Checkpoint checkpoint, final boolean fromOptimisticBlock) {
    final UInt64 finalizedSlot = checkpoint.getEpochStartSlot(spec);
    if (spec.atSlot(finalizedSlot).getMilestone().isLessThan(SpecMilestone.ALTAIR)) {
      return;
    }

    final UInt64 finalizedPeriod =
        spec.getSyncCommitteeUtilRequired(finalizedSlot)
            .computeSyncCommitteePeriod(checkpoint.getEpoch());

    lightClientStore.pruneUpdatesBefore(finalizedPeriod.minusMinZero(MAX_RETAINED_PERIODS));
  }

  private SafeFuture<Optional<LightClientUpdate>> createAndStoreUpdate(
      final SignedBeaconBlock signatureBlock, final BeaconState signatureBlockPostState) {
    final Bytes32 parentRoot = signatureBlock.getParentRoot();

    final SafeFuture<Optional<SignedBeaconBlock>> attestedBlockFuture =
        retrieveBlockByRoot.apply(parentRoot);
    final SafeFuture<Optional<BeaconState>> attestedStateFuture =
        retrieveStateByRoot.apply(parentRoot);

    return attestedBlockFuture.thenComposeCombined(
        attestedStateFuture,
        (maybeAttestedBlock, maybeAttestedState) -> {
          if (maybeAttestedBlock.isEmpty() || maybeAttestedState.isEmpty()) {
            return SafeFuture.completedFuture(Optional.empty());
          }

          final SignedBeaconBlock attestedBlock = maybeAttestedBlock.get();
          final BeaconState attestedState = maybeAttestedState.get();

          final Bytes32 finalizedRoot = attestedState.getFinalizedCheckpoint().getRoot();
          final SafeFuture<Optional<SignedBeaconBlock>> finalizedBlockFuture;
          if (finalizedRoot.isZero()) {
            finalizedBlockFuture = SafeFuture.completedFuture(Optional.empty());
          } else {
            finalizedBlockFuture = retrieveBlockByRoot.apply(finalizedRoot);
          }

          return finalizedBlockFuture.thenApply(
              maybeFinalizedBlock -> {
                if (!finalizedRoot.isZero() && maybeFinalizedBlock.isEmpty()) {
                  return Optional.empty();
                }

                final Optional<LightClientUtil> maybeLightClientUtil =
                    spec.getLightClientUtil(attestedBlock.getSlot());
                if (maybeLightClientUtil.isEmpty()) {
                  return Optional.empty();
                }
                final LightClientUtil lightClientUtil = maybeLightClientUtil.get();

                final LightClientUpdate update =
                    lightClientUtil.createLightClientUpdate(
                        signatureBlockPostState,
                        signatureBlock,
                        attestedState,
                        attestedBlock,
                        maybeFinalizedBlock);
                lightClientStore.addUpdate(update);

                final LightClientFinalityUpdate finalityUpdate =
                    lightClientUtil.createLightClientFinalityUpdate(update);
                lightClientStore.addFinalityUpdate(finalityUpdate);

                final LightClientOptimisticUpdate optimisticUpdate =
                    lightClientUtil.createLightClientOptimisticUpdate(update);
                lightClientStore.addOptimisticUpdate(optimisticUpdate);

                return Optional.of(update);
              });
        });
  }
}
