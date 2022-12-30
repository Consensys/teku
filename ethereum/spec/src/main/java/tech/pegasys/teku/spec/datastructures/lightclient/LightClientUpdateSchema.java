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

package tech.pegasys.teku.spec.datastructures.lightclient;

import tech.pegasys.teku.infrastructure.ssz.collections.SszBytes32Vector;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema7;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBytes32VectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregateSchema;
import tech.pegasys.teku.spec.datastructures.state.SyncCommittee;

public class LightClientUpdateSchema
    extends ContainerSchema7<
        LightClientUpdate,
        BeaconBlockHeader,
        SyncCommittee,
        SszBytes32Vector,
        BeaconBlockHeader,
        SszBytes32Vector,
        SyncAggregate,
        SszUInt64> {

  public LightClientUpdateSchema(final SpecConfigAltair specConfigAltair) {
    super(
        "LightClientUpdate",
        namedSchema("attested_header", BeaconBlockHeader.SSZ_SCHEMA),
        namedSchema("next_sync_committee", new SyncCommittee.SyncCommitteeSchema(specConfigAltair)),
        namedSchema(
            "next_sync_committee_branch",
            SszBytes32VectorSchema.create(specConfigAltair.getSyncCommitteeBranchLength())),
        namedSchema("finalized_header", BeaconBlockHeader.SSZ_SCHEMA),
        namedSchema(
            "finality_branch",
            SszBytes32VectorSchema.create(specConfigAltair.getFinalityBranchLength())),
        namedSchema(
            "sync_aggregate", SyncAggregateSchema.create(specConfigAltair.getSyncCommitteeSize())),
        namedSchema("signature_slot", SszPrimitiveSchemas.UINT64_SCHEMA));
  }

  public LightClientUpdate create(
      BeaconBlockHeader attestedHeader,
      SyncCommittee nextSyncCommittee,
      SszBytes32Vector nextSyncCommitteeBranch,
      BeaconBlockHeader finalizedHeader,
      SszBytes32Vector finalityBranch,
      SyncAggregate syncAggregate,
      SszUInt64 signatureSlot) {
    return new LightClientUpdate(
        this,
        attestedHeader,
        nextSyncCommittee,
        nextSyncCommitteeBranch,
        finalizedHeader,
        finalityBranch,
        syncAggregate,
        signatureSlot);
  }

  @Override
  public LightClientUpdate createFromBackingNode(TreeNode node) {
    return new LightClientUpdate(this, node);
  }

  @SuppressWarnings("unchecked")
  public SszBytes32VectorSchema<SszBytes32Vector> getSyncCommitteeBranchSchema() {
    return (SszBytes32VectorSchema<SszBytes32Vector>) getChildSchema(2);
  }

  @SuppressWarnings("unchecked")
  public SszBytes32VectorSchema<SszBytes32Vector> getFinalityBranchSchema() {
    return (SszBytes32VectorSchema<SszBytes32Vector>) getChildSchema(4);
  }
}
