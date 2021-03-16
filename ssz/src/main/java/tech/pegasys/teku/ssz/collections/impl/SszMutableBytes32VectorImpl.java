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

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ssz.cache.IntCache;
import tech.pegasys.teku.ssz.collections.SszBytes32Vector;
import tech.pegasys.teku.ssz.collections.SszMutableBytes32Vector;
import tech.pegasys.teku.ssz.impl.AbstractSszComposite;
import tech.pegasys.teku.ssz.primitive.SszBytes32;
import tech.pegasys.teku.ssz.tree.TreeNode;

public class SszMutableBytes32VectorImpl extends SszMutablePrimitiveVectorImpl<Bytes32, SszBytes32>
    implements SszMutableBytes32Vector {

  public SszMutableBytes32VectorImpl(AbstractSszComposite<SszBytes32> backingImmutableData) {
    super(backingImmutableData);
  }

  @Override
  public SszBytes32Vector commitChanges() {
    return (SszBytes32Vector) super.commitChanges();
  }

  @Override
  protected SszBytes32VectorImpl createImmutableSszComposite(
      TreeNode backingNode, IntCache<SszBytes32> childrenCache) {
    return new SszBytes32VectorImpl(getSchema(), backingNode);
  }

  @Override
  public SszMutableBytes32VectorImpl createWritableCopy() {
    throw new UnsupportedOperationException(
        "Creating a writable copy from writable instance is not supported");
  }
}
