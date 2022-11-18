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

import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteVector;
import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class AccessTuple extends Container2<AccessTuple, SszByteVector, SszList<SszBytes32>> {

  private static final long MAX_ACCESS_LIST_STORAGE_KEYS = 16777216; // 2**24

  public static class AccessTupleSchema
      extends ContainerSchema2<AccessTuple, SszByteVector, SszList<SszBytes32>> {

    public AccessTupleSchema() {
      super(
          "AccessTuple",
          namedSchema("address", SszByteVectorSchema.create(Bytes20.SIZE)),
          namedSchema(
              "storage_keys",
              SszListSchema.create(
                  SszPrimitiveSchemas.BYTES32_SCHEMA, MAX_ACCESS_LIST_STORAGE_KEYS)));
    }

    @SuppressWarnings("unchecked")
    public SszListSchema<SszBytes32, ?> getStorageKeysSchema() {
      return (SszListSchema<SszBytes32, ?>) getFieldSchema1();
    }

    @Override
    public AccessTuple createFromBackingNode(TreeNode node) {
      return new AccessTuple(this, node);
    }
  }

  public static final AccessTuple.AccessTupleSchema SSZ_SCHEMA =
      new AccessTuple.AccessTupleSchema();

  AccessTuple(final AccessTupleSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }
}
