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

package tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc;

import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfigEip7805;

public class InclusionListByCommitteeRequestMessageSchema
    extends ContainerSchema2<InclusionListByCommitteeRequestMessage, SszUInt64, SszBitvector> {

  public InclusionListByCommitteeRequestMessageSchema(final SpecConfigEip7805 specConfigEip7805) {
    super(
        "InclusionListByCommitteeRequestMessage",
        namedSchema("slot", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema(
            "committee_indices",
            SszBitvectorSchema.create(specConfigEip7805.getInclusionListCommitteeSize())));
  }

  @Override
  public InclusionListByCommitteeRequestMessage createFromBackingNode(final TreeNode node) {
    return new InclusionListByCommitteeRequestMessage(this, node);
  }
}
