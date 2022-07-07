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

package tech.pegasys.teku.infrastructure.ssz.collections.impl;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.cache.IntCache;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBytes32List;
import tech.pegasys.teku.infrastructure.ssz.collections.SszMutableBytes32List;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBytes32ListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class SszBytes32ListImpl extends SszPrimitiveListImpl<Bytes32, SszBytes32>
    implements SszBytes32List {

  public SszBytes32ListImpl(SszBytes32ListSchema<?> schema, TreeNode backingNode) {
    super(schema, backingNode);
  }

  SszBytes32ListImpl(
      SszListSchema<SszBytes32, ?> schema, TreeNode backingNode, IntCache<SszBytes32> cache) {
    super(schema, backingNode, cache);
  }

  @Override
  public SszMutableBytes32List createWritableCopy() {
    return new SszMutableBytes32ListImpl(this);
  }
}
