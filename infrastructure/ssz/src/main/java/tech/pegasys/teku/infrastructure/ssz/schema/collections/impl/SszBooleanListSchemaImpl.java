/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.infrastructure.ssz.schema.collections.impl;

import tech.pegasys.teku.infrastructure.ssz.collections.SszBooleanList;
import tech.pegasys.teku.infrastructure.ssz.collections.impl.SszBooleanListImpl;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBoolean;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBooleanListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class SszBooleanListSchemaImpl<SszListT extends SszBooleanList>
    extends SszPrimitiveListSchemaImpl<Boolean, SszBoolean, SszListT>
    implements SszBooleanListSchema<SszListT> {

  public SszBooleanListSchemaImpl(final long maxLength) {
    super(SszPrimitiveSchemas.BOOLEAN_SCHEMA, maxLength);
  }

  @Override
  @SuppressWarnings("unchecked")
  public SszListT createFromBackingNode(final TreeNode node) {
    return (SszListT) new SszBooleanListImpl(this, node);
  }
}
