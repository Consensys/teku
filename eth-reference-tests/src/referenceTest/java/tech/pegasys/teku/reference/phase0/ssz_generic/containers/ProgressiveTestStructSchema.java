/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.reference.phase0.ssz_generic.containers;

import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema4;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszProgressiveListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

/**
 * Schema for ProgressiveTestStruct - a regular Container with ProgressiveList fields.
 *
 * <pre>
 * class ProgressiveTestStruct(Container):
 *     A: ProgressiveList[byte]
 *     B: ProgressiveList[uint64]
 *     C: ProgressiveList[SmallTestStruct]
 *     D: ProgressiveList[ProgressiveList[VarTestStruct]]
 * </pre>
 */
public class ProgressiveTestStructSchema
    extends ContainerSchema4<
        ProgressiveTestStruct,
        SszList<SszByte>,
        SszList<SszUInt64>,
        SszList<SmallTestStruct>,
        SszList<SszList<VarTestStruct>>> {

  public ProgressiveTestStructSchema() {
    super(
        ProgressiveTestStruct.class.getSimpleName(),
        NamedSchema.of("A", SszProgressiveListSchema.create(SszPrimitiveSchemas.BYTE_SCHEMA)),
        NamedSchema.of("B", SszProgressiveListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA)),
        NamedSchema.of("C", SszProgressiveListSchema.create(new SmallTestStructSchema())),
        NamedSchema.of(
            "D",
            SszProgressiveListSchema.create(
                SszProgressiveListSchema.create(new VarTestStructSchema()))));
  }

  @Override
  public ProgressiveTestStruct createFromBackingNode(final TreeNode node) {
    return new ProgressiveTestStruct(this, node);
  }
}
