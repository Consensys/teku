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

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;
import tech.pegasys.teku.ssz.backing.containers.Container2;
import tech.pegasys.teku.ssz.backing.containers.ContainerType2;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszBytes32;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszBytes4;

public class ForkData extends Container2<ForkData, SszBytes4, SszBytes32> {

  public static class ForkDataType extends ContainerType2<ForkData, SszBytes4, SszBytes32> {

    public ForkDataType() {
      super(
          "ForkData",
          namedSchema("currentVersion", SszPrimitiveSchemas.BYTES4_SCHEMA),
          namedSchema("genesisValidatorsRoot", SszPrimitiveSchemas.BYTES32_SCHEMA));
    }

    @Override
    public ForkData createFromBackingNode(TreeNode node) {
      return new ForkData(this, node);
    }
  }

  public static final ForkDataType TYPE = new ForkDataType();

  private ForkData(ForkDataType type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public ForkData(final Bytes4 currentVersion, final Bytes32 genesisValidatorsRoot) {
    super(TYPE, new SszBytes4(currentVersion), new SszBytes32(genesisValidatorsRoot));
  }

  public Bytes4 getCurrentVersion() {
    return getField0().get();
  }

  public Bytes32 getGenesisValidatorsRoot() {
    return getField1().get();
  }
}
