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

package tech.pegasys.teku.spec.logic.common.util;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBytes32Vector;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.BeaconBlockBodyAltair;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientBootstrap;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientFinalityUpdate;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientHeader;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientOptimisticUpdate;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientUpdate;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientUpdateSchema;
import tech.pegasys.teku.spec.datastructures.state.SyncCommittee;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;

public class LightClientUtil {

  private final BeaconStateAccessorsAltair beaconStateAccessors;
  private final SyncCommitteeUtil syncCommitteeUtil;
  private final SchemaDefinitionsAltair schemaDefinitionsAltair;
  private final MiscHelpers miscHelpers;
  private final SpecConfigAltair specConfig;

  public LightClientUtil(
      final BeaconStateAccessorsAltair beaconStateAccessors,
      final SyncCommitteeUtil syncCommitteeUtil,
      final SchemaDefinitionsAltair schemaDefinitionsAltair,
      final MiscHelpers miscHelpers,
      final SpecConfigAltair specConfig) {
    this.beaconStateAccessors = beaconStateAccessors;
    this.syncCommitteeUtil = syncCommitteeUtil;
    this.schemaDefinitionsAltair = schemaDefinitionsAltair;
    this.miscHelpers = miscHelpers;
    this.specConfig = specConfig;
  }

  public LightClientBootstrap getLightClientBootstrap(final BeaconState state) {
    final UInt64 currentEpoch = beaconStateAccessors.getCurrentEpoch(state);

    // Requires rehashing the state. See
    // https://github.com/ethereum/consensus-specs/blob/master/specs/altair/light-client/full-node.md#create_light_client_bootstrap
    final LightClientHeader lightClientHeader =
        schemaDefinitionsAltair
            .getLightClientHeaderSchema()
            .create(BeaconBlockHeader.fromState(state));

    final SyncCommittee currentSyncCommittee =
        syncCommitteeUtil.getSyncCommittee(state, currentEpoch);

    final SszBytes32Vector currentSyncCommitteeProof =
        BeaconStateAltair.required(state).createCurrentSyncCommitteeProof();

    return schemaDefinitionsAltair
        .getLightClientBootstrapSchema()
        .create(lightClientHeader, currentSyncCommittee, currentSyncCommitteeProof);
  }

  /**
   * {@code create_light_client_update}. {@code finalizedBlock} is empty when the update carries no
   * finality information.
   */
  public LightClientUpdate createLightClientUpdate(
      final BeaconState state,
      final SignedBeaconBlock block,
      final BeaconState attestedState,
      final SignedBeaconBlock attestedBlock,
      final Optional<SignedBeaconBlock> finalizedBlock) {
    checkArgument(
        miscHelpers
            .computeEpochAtSlot(attestedState.getSlot())
            .isGreaterThanOrEqualTo(specConfig.getAltairForkEpoch()),
        "Attested state must be at or after the Altair fork");

    final SyncAggregate syncAggregate =
        BeaconBlockBodyAltair.required(block.getMessage().getBody()).getSyncAggregate();
    checkArgument(
        syncAggregate.getSyncCommitteeBits().getBitCount()
            >= specConfig.getMinSyncCommitteeParticipants(),
        "Sync aggregate must have at least MIN_SYNC_COMMITTEE_PARTICIPANTS participants");

    // The headers reconstructed from each state must be the blocks those states came from.
    // hashTreeRoot of a BeaconBlockHeader equals that of its BeaconBlock, so the roots compare
    // directly.
    checkArgument(
        state.getSlot().equals(state.getLatestBlockHeader().getSlot()),
        "State must be processed up to its latest block header");
    checkArgument(
        BeaconBlockHeader.fromState(state).hashTreeRoot().equals(block.getRoot()),
        "State must be the post-state of the signature block");

    checkArgument(
        attestedState.getSlot().equals(attestedState.getLatestBlockHeader().getSlot()),
        "Attested state must be processed up to its latest block header");
    final Bytes32 attestedHeaderRoot = BeaconBlockHeader.fromState(attestedState).hashTreeRoot();
    checkArgument(
        attestedHeaderRoot.equals(attestedBlock.getRoot()),
        "Attested state must be the post-state of the attested block");
    checkArgument(
        attestedHeaderRoot.equals(block.getParentRoot()),
        "Attested block must be the parent of the signature block");

    final LightClientUpdateSchema schema = schemaDefinitionsAltair.getLightClientUpdateSchema();
    final BeaconStateAltair attestedStateAltair = BeaconStateAltair.required(attestedState);

    final boolean samePeriod =
        syncCommitteePeriodAtSlot(attestedBlock.getSlot())
            .equals(syncCommitteePeriodAtSlot(block.getSlot()));
    final SyncCommittee nextSyncCommittee =
        samePeriod
            ? attestedStateAltair.getNextSyncCommittee()
            : schema.getNextSyncCommitteeSchema().getDefault();
    final SszBytes32Vector nextSyncCommitteeBranch =
        samePeriod
            ? attestedStateAltair.createNextSyncCommitteeProof()
            : schema.getSyncCommitteeBranchSchema().getDefault();

    final LightClientHeader defaultHeader =
        schemaDefinitionsAltair.getLightClientHeaderSchema().getDefault();
    final LightClientHeader finalizedHeader;
    final SszBytes32Vector finalityBranch;
    if (finalizedBlock.isPresent()) {
      final SignedBeaconBlock finalized = finalizedBlock.get();
      if (finalized.getSlot().isGreaterThan(UInt64.ZERO)) {
        checkArgument(
            finalized.getRoot().equals(attestedState.getFinalizedCheckpoint().getRoot()),
            "Finalized block must match the attested state's finalized checkpoint");
        finalizedHeader = headerFromBlock(finalized);
      } else {
        checkArgument(
            attestedState.getFinalizedCheckpoint().getRoot().isZero(),
            "Genesis finalized block requires a zeroed finalized checkpoint root");
        finalizedHeader = defaultHeader;
      }
      finalityBranch = attestedStateAltair.createFinalityBranchProof();
    } else {
      finalizedHeader = defaultHeader;
      finalityBranch = schema.getFinalityBranchSchema().getDefault();
    }

    return schema.create(
        headerFromBlock(attestedBlock),
        nextSyncCommittee,
        nextSyncCommitteeBranch,
        finalizedHeader,
        finalityBranch,
        syncAggregate,
        SszUInt64.of(block.getSlot()));
  }

  /** {@code create_light_client_finality_update}. */
  public LightClientFinalityUpdate createLightClientFinalityUpdate(final LightClientUpdate update) {
    return schemaDefinitionsAltair
        .getLightClientFinalityUpdateSchema()
        .create(
            update.getAttestedHeader(),
            update.getFinalizedHeader(),
            update.getFinalityBranch(),
            update.getSyncAggregate(),
            update.getSignatureSlot());
  }

  /** {@code create_light_client_optimistic_update}. */
  public LightClientOptimisticUpdate createLightClientOptimisticUpdate(
      final LightClientUpdate update) {
    return schemaDefinitionsAltair
        .getLightClientOptimisticUpdateSchema()
        .create(update.getAttestedHeader(), update.getSyncAggregate(), update.getSignatureSlot());
  }

  private UInt64 syncCommitteePeriodAtSlot(final UInt64 slot) {
    return syncCommitteeUtil.computeSyncCommitteePeriod(miscHelpers.computeEpochAtSlot(slot));
  }

  private LightClientHeader headerFromBlock(final SignedBeaconBlock block) {
    return schemaDefinitionsAltair
        .getLightClientHeaderSchema()
        .create(BeaconBlockHeader.fromBlock(block.getMessage()));
  }
}
