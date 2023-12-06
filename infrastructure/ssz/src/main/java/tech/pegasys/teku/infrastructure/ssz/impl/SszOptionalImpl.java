/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.infrastructure.ssz.impl;

import com.google.common.base.Objects;
import com.google.common.base.Suppliers;
import java.util.function.Supplier;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszOptional;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.SszOptionalSchemaImpl;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class SszOptionalImpl implements SszOptional {

  private final SszOptionalSchemaImpl<?> schema;
  private final TreeNode backingNode;
  private final Supplier<SszData> value = Suppliers.memoize(this::createValue);

  public SszOptionalImpl(SszOptionalSchemaImpl<?> schema, TreeNode backingNode) {
    this.schema = schema;
    this.backingNode = backingNode;
  }

  @Override
  public SszOptionalSchemaImpl<?> getSchema() {
    return schema;
  }

  @Override
  public SszData getValue() {
    return value.get();
  }

  @Override
  public TreeNode getBackingNode() {
    return backingNode;
  }

  private SszData createValue() {
    SszSchema<?> valueSchema = getSchema().getChildSchema();
    TreeNode valueNode = getSchema().getValueNode(getBackingNode());
    return valueSchema.createFromBackingNode(valueNode);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof SszOptional)) {
      return false;
    }
    SszOptional sszOptional = (SszOptional) o;
    return Objects.equal(getSchema(), sszOptional.getSchema())
        && Objects.equal(hashTreeRoot(), sszOptional.hashTreeRoot());
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(getSchema(), hashTreeRoot());
  }

  @Override
  public String toString() {
    return "SszUnion{value=" + getValue() + '}';
  }
}
