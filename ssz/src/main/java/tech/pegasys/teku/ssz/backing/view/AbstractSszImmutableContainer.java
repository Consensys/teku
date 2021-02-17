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

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Arrays;
import java.util.Objects;
import tech.pegasys.teku.ssz.backing.SszData;
import tech.pegasys.teku.ssz.backing.SszMutableContainer;
import tech.pegasys.teku.ssz.backing.cache.ArrayIntCache;
import tech.pegasys.teku.ssz.backing.cache.IntCache;
import tech.pegasys.teku.ssz.backing.schema.AbstractSszContainerSchema;
import tech.pegasys.teku.ssz.backing.schema.SszContainerSchema;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;

/** Handy base class for immutable containers */
public abstract class AbstractSszImmutableContainer extends SszContainerImpl {

  protected AbstractSszImmutableContainer(
      AbstractSszContainerSchema<? extends AbstractSszImmutableContainer> schema) {
    this(schema, schema.getDefaultTree());
  }

  protected AbstractSszImmutableContainer(
      SszContainerSchema<? extends AbstractSszImmutableContainer> schema, TreeNode backingNode) {
    super(schema, backingNode);
  }

  protected AbstractSszImmutableContainer(
      SszContainerSchema<? extends AbstractSszImmutableContainer> schema, SszData... memberValues) {
    super(
        schema,
        schema.createTreeFromFieldValues(Arrays.asList(memberValues)),
        createCache(memberValues));
    checkArgument(
        memberValues.length == this.getSchema().getMaxLength(),
        "Wrong number of member values: %s",
        memberValues.length);
    for (int i = 0; i < memberValues.length; i++) {
      checkArgument(
          memberValues[i].getSchema().equals(schema.getChildSchema(i)),
          "Wrong child schema at index %s. Expected: %s, was %s",
          i,
          schema.getChildSchema(i),
          memberValues[i].getSchema());
    }
  }

  private static IntCache<SszData> createCache(SszData... memberValues) {
    ArrayIntCache<SszData> cache = new ArrayIntCache<>(memberValues.length);
    for (int i = 0; i < memberValues.length; i++) {
      cache.invalidateWithNewValue(i, memberValues[i]);
    }
    return cache;
  }

  @Override
  public SszMutableContainer createWritableCopy() {
    throw new UnsupportedOperationException(
        "This container doesn't support mutable structure: " + getClass().getName());
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof AbstractSszImmutableContainer)) {
      return false;
    }

    AbstractSszImmutableContainer other = (AbstractSszImmutableContainer) obj;
    return hashTreeRoot().equals(other.hashTreeRoot());
  }

  @Override
  public int hashCode() {
    return hashTreeRoot().slice(0, 4).toInt();
  }
}
