/*
 * Copyright Consensys Software Inc., 2023
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

import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfigElectra;

public class DataColumnSidecarsByRootRequestMessageSchema
    extends AbstractSszListSchema<DataColumnIdentifier, DataColumnSidecarsByRootRequestMessage> {

  public DataColumnSidecarsByRootRequestMessageSchema(final SpecConfigElectra specConfigElectra) {
    super(DataColumnIdentifier.SSZ_SCHEMA, specConfigElectra.getMaxRequestDataColumnSidecars());
  }

  @Override
  public DataColumnSidecarsByRootRequestMessage createFromBackingNode(final TreeNode node) {
    return new DataColumnSidecarsByRootRequestMessage(this, node);
  }
}
