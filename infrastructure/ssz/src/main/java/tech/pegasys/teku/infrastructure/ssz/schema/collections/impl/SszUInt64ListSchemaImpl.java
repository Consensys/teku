/*
 * Copyright 2021 ConsenSys AG.
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

import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64List;
import tech.pegasys.teku.infrastructure.ssz.collections.impl.SszUInt64ListImpl;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszUInt64ListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class SszUInt64ListSchemaImpl<SszListT extends SszUInt64List>
    extends AbstractSszListSchema<SszUInt64, SszListT> implements SszUInt64ListSchema<SszListT> {

  public SszUInt64ListSchemaImpl(long maxLength) {
    super(SszPrimitiveSchemas.UINT64_SCHEMA, maxLength);
  }

  @Override
  @SuppressWarnings("unchecked")
  public SszListT createFromBackingNode(TreeNode node) {
    return (SszListT) new SszUInt64ListImpl(this, node);
  }
}
