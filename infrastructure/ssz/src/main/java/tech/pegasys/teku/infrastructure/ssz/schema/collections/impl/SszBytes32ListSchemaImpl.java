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

package tech.pegasys.teku.infrastructure.ssz.schema.collections.impl;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBytes32List;
import tech.pegasys.teku.infrastructure.ssz.collections.impl.SszBytes32ListImpl;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBytes32ListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class SszBytes32ListSchemaImpl<SszListT extends SszBytes32List>
    extends SszPrimitiveListSchemaImpl<Bytes32, SszBytes32, SszListT>
    implements SszBytes32ListSchema<SszListT> {

  public SszBytes32ListSchemaImpl(final long maxLength) {
    super(SszPrimitiveSchemas.BYTES32_SCHEMA, maxLength);
  }

  @Override
  @SuppressWarnings("unchecked")
  public SszListT createFromBackingNode(final TreeNode node) {
    return (SszListT) new SszBytes32ListImpl(this, node);
  }
}
