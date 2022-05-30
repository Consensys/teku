/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.infrastructure.ssz.schema.impl;

import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszContainer;
import tech.pegasys.teku.infrastructure.ssz.impl.AbstractSszImmutableContainer;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class SszContainerStorage<C extends SszContainer> extends AbstractSszImmutableContainer {

  SszContainerStorage(final SszContainerStorageSchema<C> schema) {
    super(schema);
  }

  SszContainerStorage(final SszContainerStorageSchema<C> schema, final TreeNode node) {
    super(schema, node);
  }

  @Override
  public SszContainerStorageSchema<C> getSchema() {
    return (SszContainerStorageSchema<C>) super.getSchema();
  }

  public C loadFully(final Function<Bytes32, Bytes> partLoader) {
    return getSchema().loadFully(partLoader, this);
  }
}
