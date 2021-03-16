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

package tech.pegasys.teku.ssz.backing.schema.collections.impl;

import tech.pegasys.teku.ssz.backing.collections.SszBytes32Vector;
import tech.pegasys.teku.ssz.backing.collections.impl.SszBytes32VectorImpl;
import tech.pegasys.teku.ssz.backing.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.backing.schema.collections.SszBytes32VectorSchema;
import tech.pegasys.teku.ssz.backing.schema.impl.AbstractSszVectorSchema;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszBytes32;

public class SszBytes32VectorSchemaImpl<SszVectorT extends SszBytes32Vector>
    extends AbstractSszVectorSchema<SszBytes32, SszVectorT>
    implements SszBytes32VectorSchema<SszVectorT> {

  public SszBytes32VectorSchemaImpl(long vectorLength) {
    super(SszPrimitiveSchemas.BYTES32_SCHEMA, vectorLength);
  }

  @Override
  @SuppressWarnings("unchecked")
  public SszVectorT createFromBackingNode(TreeNode node) {
    return (SszVectorT) new SszBytes32VectorImpl(this, node);
  }
}
