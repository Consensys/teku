/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.teku.spec.datastructures.operations;

import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.ssz.backing.containers.Container2;
import tech.pegasys.teku.ssz.backing.containers.ContainerSchema2;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;

public class ProposerSlashing
    extends Container2<ProposerSlashing, SignedBeaconBlockHeader, SignedBeaconBlockHeader> {

  public static class ProposerSlashingSchema
      extends ContainerSchema2<ProposerSlashing, SignedBeaconBlockHeader, SignedBeaconBlockHeader> {

    public ProposerSlashingSchema() {
      super(
          "ProposerSlashing",
          namedSchema("header_1", SignedBeaconBlockHeader.SSZ_SCHEMA),
          namedSchema("header_2", SignedBeaconBlockHeader.SSZ_SCHEMA));
    }

    @Override
    public ProposerSlashing createFromBackingNode(TreeNode node) {
      return new ProposerSlashing(this, node);
    }
  }

  public static final ProposerSlashingSchema SSZ_SCHEMA = new ProposerSlashingSchema();

  private ProposerSlashing(ProposerSlashingSchema type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public ProposerSlashing(SignedBeaconBlockHeader header_1, SignedBeaconBlockHeader header_2) {
    super(SSZ_SCHEMA, header_1, header_2);
  }

  public SignedBeaconBlockHeader getHeader_1() {
    return getField0();
  }

  public SignedBeaconBlockHeader getHeader_2() {
    return getField1();
  }
}
