/*
 * Copyright ConsenSys Software Inc., 2023
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

import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema1;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;

public class LightClientHeaderSchema
    extends ContainerSchema1<LightClientHeader, BeaconBlockHeader> {

  public LightClientHeaderSchema() {
    super("LightClientHeader", namedSchema("beacon", BeaconBlockHeader.SSZ_SCHEMA));
  }

  public LightClientHeader create(BeaconBlockHeader beaconBlockHeader) {
    return new LightClientHeader(this, beaconBlockHeader);
  }

  @Override
  public LightClientHeader createFromBackingNode(TreeNode node) {
    return new LightClientHeader(this, node);
  }
}
