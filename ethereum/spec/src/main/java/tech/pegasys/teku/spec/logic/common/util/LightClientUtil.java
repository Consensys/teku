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

package tech.pegasys.teku.spec.logic.common.util;

import java.util.List;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBytes32Vector;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBytes32VectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.MerkleUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientBootstrap;
import tech.pegasys.teku.spec.datastructures.state.SyncCommittee;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;

public class LightClientUtil {

  private final BeaconStateAccessorsAltair beaconStateAccessors;
  private final SyncCommitteeUtil syncCommitteeUtil;
  private final SchemaDefinitionsAltair schemaDefinitionsAltair;

  public LightClientUtil(
      final BeaconStateAccessorsAltair beaconStateAccessors,
      final SyncCommitteeUtil syncCommitteeUtil,
      final SchemaDefinitionsAltair schemaDefinitionsAltair) {
    this.beaconStateAccessors = beaconStateAccessors;
    this.syncCommitteeUtil = syncCommitteeUtil;
    this.schemaDefinitionsAltair = schemaDefinitionsAltair;
  }

  public LightClientBootstrap getLightClientBootstrap(final BeaconState state) {
    final UInt64 currentEpoch = beaconStateAccessors.getCurrentEpoch(state);
    final BeaconBlockHeader latestBlockHeader = state.getLatestBlockHeader();

    // Requires rehashing the state. See
    // https://github.com/ethereum/consensus-specs/blob/dev/specs/altair/light-client/full-node.md#create_light_client_bootstrap
    final BeaconBlockHeader bootstrapBlockHeader =
        new BeaconBlockHeader(
            latestBlockHeader.getSlot(),
            latestBlockHeader.getProposerIndex(),
            latestBlockHeader.getParentRoot(),
            state.hashTreeRoot(),
            latestBlockHeader.getBodyRoot());

    final SyncCommittee currentSyncCommittee =
        syncCommitteeUtil.getSyncCommittee(state, currentEpoch);

    final List<Bytes32> currentSyncCommitteeProof =
        MerkleUtil.constructMerkleProof(
            state.getBackingNode(),
            state
                .getSchema()
                .getChildGeneralizedIndex(
                    state.getSchema().getFieldIndex(BeaconStateFields.CURRENT_SYNC_COMMITTEE)));

    final SszBytes32Vector currentSyncCommitteeProofSsz =
        SszBytes32VectorSchema.create(currentSyncCommitteeProof.size())
            .createFromElements(
                currentSyncCommitteeProof.stream()
                    .map(bytes32 -> SszBytes32.of(bytes32))
                    .collect(Collectors.toList()));

    return schemaDefinitionsAltair
        .getLightClientBootstrapSchema()
        .create(bootstrapBlockHeader, currentSyncCommittee, currentSyncCommitteeProofSsz);
  }
}
