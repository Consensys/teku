/*
 * Copyright Consensys Software Inc., 2024
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

import static com.google.common.base.Preconditions.checkArgument;

import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszStableContainer;
import tech.pegasys.teku.infrastructure.ssz.cache.IntCache;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.schema.SszStableContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public abstract class AbstractSszStableContainer extends SszContainerImpl
    implements SszStableContainer {
  private final Supplier<Bytes32> hashTreeRootSupplier =
      () ->
          Hash.getSha256Instance()
              .wrappedDigest(super.hashTreeRoot(), getActiveFields().hashTreeRoot());

  public AbstractSszStableContainer(SszStableContainerSchema<?> type) {
    super(type);
  }

  public AbstractSszStableContainer(SszStableContainerSchema<?> type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public AbstractSszStableContainer(
      SszStableContainerSchema<?> type, TreeNode backingNode, IntCache<SszData> cache) {
    super(type, backingNode, cache);
  }

  @Override
  public final SszData get(int index) {
    checkArgument(isFieldActive(index), "Field is not active");
    return super.get(index);
  }

  @Override
  public boolean isFieldActive(int index) {
    return getStableSchema().isActiveField(index);
  }

  @Override
  public Bytes32 hashTreeRoot() {
    return hashTreeRootSupplier.get();
  }

  private SszBitvector getActiveFields() {
    return getStableSchema().getActiveFields();
  }

  private SszStableContainerSchema<?> getStableSchema() {
    return ((SszStableContainerSchema<?>) super.getSchema());
  }
}
