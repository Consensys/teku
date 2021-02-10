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

package tech.pegasys.teku.ssz.backing.view;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Objects;
import tech.pegasys.teku.ssz.backing.SszPrimitive;
import tech.pegasys.teku.ssz.backing.schema.AbstractSszPrimitiveSchema;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;

public abstract class AbstractSszPrimitive<C, V extends AbstractSszPrimitive<C, V>>
    implements SszPrimitive<C> {
  private final AbstractSszPrimitiveSchema<V> schema;
  private final C value;

  protected AbstractSszPrimitive(C value, AbstractSszPrimitiveSchema<V> schema) {
    checkNotNull(value);
    this.schema = schema;
    this.value = value;
  }

  @Override
  public C get() {
    return value;
  }

  @Override
  public AbstractSszPrimitiveSchema<V> getSchema() {
    return schema;
  }

  @Override
  public TreeNode getBackingNode() {
    return getSchema().createBackingNode(getThis());
  }

  @SuppressWarnings("unchecked")
  protected V getThis() {
    return (V) this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    AbstractSszPrimitive<?, ?> that = (AbstractSszPrimitive<?, ?>) o;
    return Objects.equals(get(), that.get());
  }

  @Override
  public int hashCode() {
    return Objects.hash(get());
  }

  @Override
  public String toString() {
    return get().toString();
  }
}
