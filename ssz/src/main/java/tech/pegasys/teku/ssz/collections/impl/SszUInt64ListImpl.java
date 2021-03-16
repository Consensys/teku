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

package tech.pegasys.teku.ssz.collections.impl;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.cache.IntCache;
import tech.pegasys.teku.ssz.collections.SszMutableUInt64List;
import tech.pegasys.teku.ssz.collections.SszUInt64List;
import tech.pegasys.teku.ssz.impl.SszPrimitives.SszUInt64;
import tech.pegasys.teku.ssz.schema.SszListSchema;
import tech.pegasys.teku.ssz.schema.collections.SszUInt64ListSchema;
import tech.pegasys.teku.ssz.tree.TreeNode;

public class SszUInt64ListImpl extends SszPrimitiveListImpl<UInt64, SszUInt64>
    implements SszUInt64List {

  public SszUInt64ListImpl(SszUInt64ListSchema<?> schema, TreeNode backingNode) {
    super(schema, backingNode);
  }

  SszUInt64ListImpl(
      SszListSchema<SszUInt64, ?> schema, TreeNode backingNode, IntCache<SszUInt64> cache) {
    super(schema, backingNode, cache);
  }

  @Override
  public SszMutableUInt64List createWritableCopy() {
    return new SszMutableUInt64ListImpl(this);
  }
}
