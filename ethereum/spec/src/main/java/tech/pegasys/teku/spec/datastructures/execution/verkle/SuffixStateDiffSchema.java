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

package tech.pegasys.teku.spec.datastructures.execution.verkle;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszOptional;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.schema.SszFieldName;
import tech.pegasys.teku.infrastructure.ssz.schema.SszOptionalSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class SuffixStateDiffSchema
    extends ContainerSchema3<
        SuffixStateDiff, SszByte, SszOptional<SszBytes32>, SszOptional<SszBytes32>> {

  static final SszFieldName FIELD_CURRENT_VALUE = () -> "current_value";

  SuffixStateDiffSchema() {
    super(
        "SuffixStateDiff",
        namedSchema("suffix", SszPrimitiveSchemas.BYTE_SCHEMA),
        namedSchema(
            FIELD_CURRENT_VALUE, SszOptionalSchema.create(SszPrimitiveSchemas.BYTES32_SCHEMA)),
        namedSchema("new_value", SszOptionalSchema.create(SszPrimitiveSchemas.BYTES32_SCHEMA)));
  }

  public static final SuffixStateDiffSchema INSTANCE = new SuffixStateDiffSchema();

  @SuppressWarnings("unchecked")
  public SszOptionalSchema<SszBytes32, SszOptional<SszBytes32>> getCurrentValueSchema() {
    return (SszOptionalSchema<SszBytes32, SszOptional<SszBytes32>>)
        getChildSchema(getFieldIndex(FIELD_CURRENT_VALUE));
  }

  public SuffixStateDiff create(
      final Byte suffix, final Optional<Bytes32> currentValue, final Optional<Bytes32> newValue) {
    return new SuffixStateDiff(this, suffix, currentValue, newValue);
  }

  @Override
  public SuffixStateDiff createFromBackingNode(TreeNode node) {
    return new SuffixStateDiff(this, node);
  }
}
