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

package tech.pegasys.teku.datastructures.operations;

import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.backing.containers.Container2;
import tech.pegasys.teku.ssz.backing.containers.ContainerType2;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;
import tech.pegasys.teku.ssz.sos.SszTypeDescriptor;

public class ProposerSlashing
    extends Container2<ProposerSlashing, SignedBeaconBlockHeader, SignedBeaconBlockHeader>
    implements SimpleOffsetSerializable, SSZContainer {

  public static class ProposerSlashingType
      extends ContainerType2<ProposerSlashing, SignedBeaconBlockHeader, SignedBeaconBlockHeader> {

    public ProposerSlashingType() {
      super(
          "ProposerSlashing",
          namedType("header_1", SignedBeaconBlockHeader.TYPE),
          namedType("header_2", SignedBeaconBlockHeader.TYPE));
    }

    @Override
    public ProposerSlashing createFromBackingNode(TreeNode node) {
      return new ProposerSlashing(this, node);
    }
  }

  @SszTypeDescriptor public static final ProposerSlashingType TYPE = new ProposerSlashingType();

  private ProposerSlashing(ProposerSlashingType type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public ProposerSlashing(SignedBeaconBlockHeader header_1, SignedBeaconBlockHeader header_2) {
    super(TYPE, header_1, header_2);
  }

  public SignedBeaconBlockHeader getHeader_1() {
    return getField0();
  }

  public SignedBeaconBlockHeader getHeader_2() {
    return getField1();
  }
}
