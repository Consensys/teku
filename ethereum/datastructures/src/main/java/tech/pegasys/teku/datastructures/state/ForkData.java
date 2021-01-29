/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.datastructures.state;

import com.google.common.base.MoreObjects;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.backing.containers.Container2;
import tech.pegasys.teku.ssz.backing.containers.ContainerType2;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.BasicViewTypes;
import tech.pegasys.teku.ssz.backing.view.BasicViews.Bytes32View;
import tech.pegasys.teku.ssz.backing.view.BasicViews.Bytes4View;
import tech.pegasys.teku.ssz.sos.SszTypeDescriptor;

public class ForkData extends Container2<ForkData, Bytes4View, Bytes32View>
    implements SimpleOffsetSerializable, SSZContainer {

  static class ForkDataType extends ContainerType2<ForkData, Bytes4View, Bytes32View> {

    public ForkDataType() {
      super(BasicViewTypes.BYTES4_TYPE, BasicViewTypes.BYTES32_TYPE);
    }

    @Override
    public ForkData createFromBackingNode(TreeNode node) {
      return new ForkData(this, node);
    }
  }

  @SszTypeDescriptor public static final ForkDataType TYPE = new ForkDataType();

  private ForkData(ForkDataType type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public ForkData(final Bytes4 currentVersion, final Bytes32 genesisValidatorsRoot) {
    super(TYPE, new Bytes4View(currentVersion), new Bytes32View(genesisValidatorsRoot));
  }

  public Bytes4 getCurrentVersion() {
    return getField0().get();
  }

  public Bytes32 getGenesisValidatorsRoot() {
    return getField1().get();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("currentVersion", getCurrentVersion())
        .add("genesisValidatorsRoot", getGenesisValidatorsRoot())
        .toString();
  }
}
