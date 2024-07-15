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

import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszStableContainer;
import tech.pegasys.teku.infrastructure.ssz.cache.IntCache;
import tech.pegasys.teku.infrastructure.ssz.schema.SszStableContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class SszStableContainerImpl extends SszStableContainerBaseImpl
    implements SszStableContainer {

  public SszStableContainerImpl(final SszStableContainerSchema<? extends SszStableContainer> type) {
    super(type);
  }

  public SszStableContainerImpl(
      final SszStableContainerSchema<? extends SszStableContainer> type,
      final TreeNode backingNode) {
    super(type, backingNode);
  }

  public SszStableContainerImpl(
      final SszStableContainerSchema<?> type,
      final TreeNode backingNode,
      final IntCache<SszData> cache) {
    super(type, backingNode, cache);
  }
}
