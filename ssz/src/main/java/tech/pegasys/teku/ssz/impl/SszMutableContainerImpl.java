/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.ssz.impl;

import java.util.Map;
import java.util.stream.Stream;
import tech.pegasys.teku.ssz.SszContainer;
import tech.pegasys.teku.ssz.SszData;
import tech.pegasys.teku.ssz.SszMutableContainer;
import tech.pegasys.teku.ssz.SszMutableData;
import tech.pegasys.teku.ssz.SszMutableRefContainer;
import tech.pegasys.teku.ssz.cache.IntCache;
import tech.pegasys.teku.ssz.schema.impl.AbstractSszContainerSchema;
import tech.pegasys.teku.ssz.tree.TreeNode;
import tech.pegasys.teku.ssz.tree.TreeUpdates;

public class SszMutableContainerImpl extends AbstractSszMutableComposite<SszData, SszMutableData>
    implements SszMutableRefContainer {

  public SszMutableContainerImpl(SszContainerImpl backingImmutableView) {
    super(backingImmutableView);
  }

  @Override
  protected SszContainerImpl createImmutableSszComposite(
      TreeNode backingNode, IntCache<SszData> viewCache) {
    return new SszContainerImpl(getSchema(), backingNode, viewCache);
  }

  @Override
  public AbstractSszContainerSchema<?> getSchema() {
    return (AbstractSszContainerSchema<?>) super.getSchema();
  }

  @Override
  public SszContainer commitChanges() {
    return (SszContainer) super.commitChanges();
  }

  @Override
  public SszMutableContainer createWritableCopy() {
    throw new UnsupportedOperationException(
        "createWritableCopy() is now implemented for immutable SszData only");
  }

  @Override
  protected void checkIndex(int index, boolean set) {
    if (index >= size()) {
      throw new IndexOutOfBoundsException(
          "Invalid index " + index + " for container with size " + size());
    }
  }

  @Override
  protected TreeUpdates packChanges(
      Stream<Map.Entry<Integer, SszData>> newChildValues, TreeNode original) {
    throw new UnsupportedOperationException("Packed values are not supported");
  }
}
