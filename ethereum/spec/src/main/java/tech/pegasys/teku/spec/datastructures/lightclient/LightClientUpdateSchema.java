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

package tech.pegasys.teku.spec.datastructures.lightclient;

import static tech.pegasys.teku.spec.constants.LightClientConstants.FINALIZED_ROOT_GINDEX;
import static tech.pegasys.teku.spec.constants.LightClientConstants.NEXT_SYNC_COMMITTEE_GINDEX;

import tech.pegasys.teku.infrastructure.ssz.collections.SszBytes32Vector;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema7;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBytes32VectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregateSchema;
import tech.pegasys.teku.spec.datastructures.state.SyncCommittee;
import tech.pegasys.teku.spec.logic.common.helpers.MathHelpers;

public class LightClientUpdateSchema
    extends ContainerSchema7<
        LightClientUpdate,
        LightClientHeader,
        SyncCommittee,
        SszBytes32Vector,
        LightClientHeader,
        SszBytes32Vector,
        SyncAggregate,
        SszUInt64> {

  public LightClientUpdateSchema(final SpecConfigAltair specConfigAltair) {
    super(
        "LightClientUpdate",
        namedSchema("attested_header", new LightClientHeaderSchema()),
        namedSchema("next_sync_committee", new SyncCommittee.SyncCommitteeSchema(specConfigAltair)),
        namedSchema(
            "next_sync_committee_branch",
            SszBytes32VectorSchema.create(MathHelpers.floorLog2(NEXT_SYNC_COMMITTEE_GINDEX))),
        namedSchema("finalized_header", new LightClientHeaderSchema()),
        namedSchema(
            "finality_branch",
            SszBytes32VectorSchema.create(MathHelpers.floorLog2(FINALIZED_ROOT_GINDEX))),
        namedSchema(
            "sync_aggregate", SyncAggregateSchema.create(specConfigAltair.getSyncCommitteeSize())),
        namedSchema("signature_slot", SszPrimitiveSchemas.UINT64_SCHEMA));
  }

  public LightClientUpdate create(
      final LightClientHeader attestedHeader,
      final SyncCommittee nextSyncCommittee,
      final SszBytes32Vector nextSyncCommitteeBranch,
      final LightClientHeader finalizedHeader,
      final SszBytes32Vector finalityBranch,
      final SyncAggregate syncAggregate,
      final SszUInt64 signatureSlot) {
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
  public LightClientUpdate createFromBackingNode(final TreeNode node) {
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
