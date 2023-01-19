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
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBytes32VectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.datastructures.state.SyncCommittee;

public class LightClientBootstrapSchema
    extends ContainerSchema3<
        LightClientBootstrap, LightClientHeader, SyncCommittee, SszBytes32Vector> {

  public LightClientBootstrapSchema(final SpecConfigAltair specConfigAltair) {
    super(
        "LightClientBootstrap",
        namedSchema("header", new LightClientHeaderSchema()),
        namedSchema(
            "current_sync_committee", new SyncCommittee.SyncCommitteeSchema(specConfigAltair)),
        namedSchema(
            "current_sync_committee_branch",
            SszBytes32VectorSchema.create(specConfigAltair.getSyncCommitteeBranchLength())));
  }

  public LightClientBootstrap create(
      LightClientHeader lightClientHeader,
      SyncCommittee syncCommittee,
      SszBytes32Vector syncCommitteeBranch) {
    return new LightClientBootstrap(this, lightClientHeader, syncCommittee, syncCommitteeBranch);
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
