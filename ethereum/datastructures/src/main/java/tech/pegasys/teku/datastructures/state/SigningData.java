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

package tech.pegasys.teku.datastructures.state;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ssz.backing.containers.Container2;
import tech.pegasys.teku.ssz.backing.containers.ContainerSchema2;
import tech.pegasys.teku.ssz.backing.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszBytes32;

public class SigningData extends Container2<SigningData, SszBytes32, SszBytes32> {

  public static class SigningDataType
      extends ContainerSchema2<SigningData, SszBytes32, SszBytes32> {

    public SigningDataType() {
      super(
          "SigningData",
          namedSchema("object_root", SszPrimitiveSchemas.BYTES32_SCHEMA),
          namedSchema("domain", SszPrimitiveSchemas.BYTES32_SCHEMA));
    }

    @Override
    public SigningData createFromBackingNode(TreeNode node) {
      return new SigningData(this, node);
    }
  }

  public static final SigningDataType SSZ_SCHEMA = new SigningDataType();

  private SigningData(SigningDataType type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public SigningData(Bytes32 object_root, Bytes32 domain) {
    super(SSZ_SCHEMA, new SszBytes32(object_root), new SszBytes32(domain));
  }

  public Bytes32 getObjectRoot() {
    return getField0().get();
  }

  public Bytes32 getDomain() {
    return getField1().get();
  }
}
