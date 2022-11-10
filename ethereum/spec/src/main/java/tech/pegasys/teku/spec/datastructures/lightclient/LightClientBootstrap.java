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
import tech.pegasys.teku.infrastructure.ssz.containers.Container3;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBytes32VectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.state.SyncCommittee;

public class LightClientBootstrap
    extends Container3<LightClientBootstrap, BeaconBlockHeader, SyncCommittee, SszBytes32Vector> {

  public static class LightClientBootstrapSchema
      extends ContainerSchema3<
          LightClientBootstrap, BeaconBlockHeader, SyncCommittee, SszBytes32Vector> {

    public LightClientBootstrapSchema(final SpecConfigAltair specConfigAltair) {
      super(
          "LightClientBootstrap",
          namedSchema("header", BeaconBlockHeader.SSZ_SCHEMA),
          namedSchema(
              "current_sync_committee", new SyncCommittee.SyncCommitteeSchema(specConfigAltair)),
          namedSchema(
              "current_sync_committee_branch",
              SszBytes32VectorSchema.create(specConfigAltair.getSyncCommitteeBranchLength())));
    }

    public LightClientBootstrap create(
        BeaconBlockHeader beaconBlockHeader,
        SyncCommittee syncCommittee,
        SszBytes32Vector syncCommitteeBranch) {
      return new LightClientBootstrap(this, beaconBlockHeader, syncCommittee, syncCommitteeBranch);
    }

    @SuppressWarnings("unchecked")
    public SszBytes32VectorSchema<SszBytes32Vector> getSyncCommitteeBranchSchema() {
      return (SszBytes32VectorSchema<SszBytes32Vector>) getChildSchema(2);
    }

    @Override
    public LightClientBootstrap createFromBackingNode(TreeNode node) {
      return new LightClientBootstrap(this, node);
    }
  }

  protected LightClientBootstrap(
      final LightClientBootstrapSchema schema,
      final BeaconBlockHeader beaconBlockHeader,
      final SyncCommittee syncCommittee,
      final SszBytes32Vector syncCommitteeBranch) {
    super(schema, beaconBlockHeader, syncCommittee, syncCommitteeBranch);
  }

  private LightClientBootstrap(final LightClientBootstrapSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }
}
