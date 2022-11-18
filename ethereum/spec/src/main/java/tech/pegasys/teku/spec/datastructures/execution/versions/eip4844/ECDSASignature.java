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

package tech.pegasys.teku.spec.datastructures.execution.versions.eip4844;

import tech.pegasys.teku.infrastructure.ssz.containers.Container3;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBit;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt256;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class ECDSASignature extends Container3<ECDSASignature, SszBit, SszUInt256, SszUInt256> {

  public static class ECDSASignatureSchema
      extends ContainerSchema3<ECDSASignature, SszBit, SszUInt256, SszUInt256> {

    public ECDSASignatureSchema() {
      super(
          "ECDSASignature",
          namedSchema("y_parity", SszPrimitiveSchemas.BIT_SCHEMA),
          namedSchema("r", SszPrimitiveSchemas.UINT256_SCHEMA),
          namedSchema("s", SszPrimitiveSchemas.UINT256_SCHEMA));
    }

    @Override
    public ECDSASignature createFromBackingNode(TreeNode node) {
      return new ECDSASignature(this, node);
    }
  }

  public static final ECDSASignature.ECDSASignatureSchema SSZ_SCHEMA =
      new ECDSASignature.ECDSASignatureSchema();

  ECDSASignature(final ECDSASignatureSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }
}
