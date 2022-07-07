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
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBytes32ListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class SszMutableBytes32ListImpl extends SszMutablePrimitiveListImpl<Bytes32, SszBytes32>
    implements SszMutableBytes32List {

  public SszMutableBytes32ListImpl(SszBytes32ListImpl backingImmutableData) {
    super(backingImmutableData);
  }

  @Override
  public SszBytes32List commitChanges() {
    return (SszBytes32List) super.commitChanges();
  }

  @Override
  protected SszBytes32ListImpl createImmutableSszComposite(
      TreeNode backingNode, IntCache<SszBytes32> childrenCache) {
    return new SszBytes32ListImpl(getSchema(), backingNode, childrenCache);
  }

  @Override
  public SszBytes32ListSchema<?> getSchema() {
    return (SszBytes32ListSchema<?>) super.getSchema();
  }

  @Override
  public SszMutableBytes32ListImpl createWritableCopy() {
    throw new UnsupportedOperationException(
        "Creating a writable copy from writable instance is not supported");
  }
}
