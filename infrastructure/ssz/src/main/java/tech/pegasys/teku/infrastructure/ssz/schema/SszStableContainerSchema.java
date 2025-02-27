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

package tech.pegasys.teku.infrastructure.ssz.schema;

import static tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszContainerSchema.namedSchema;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.IntStream;
import tech.pegasys.teku.infrastructure.ssz.SszStableContainer;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszContainerSchema.NamedSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszStableContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public interface SszStableContainerSchema<C extends SszStableContainer>
    extends SszStableContainerBaseSchema<C> {

  /**
   * Creates a new {@link SszStableContainer} schema with specified field schemas and container
   * instance constructor
   */
  static <C extends SszStableContainer> SszStableContainerSchema<C> create(
      final String name,
      final List<NamedSchema<?>> activeChildrenSchemas,
      final int maxFieldCount,
      final BiFunction<SszStableContainerSchema<C>, TreeNode, C> instanceCtor) {
    return new AbstractSszStableContainerSchema<>(name, activeChildrenSchemas, maxFieldCount) {
      @Override
      public C createFromBackingNode(TreeNode node) {
        return instanceCtor.apply(this, node);
      }
    };
  }

  /**
   * Creates a new {@link SszStableContainer} schema with specified field schemas. It is designed to
   * be used in profile schema creation only. There will be no ssz views for it.
   */
  static <C extends SszStableContainer>
      SszStableContainerSchema<C> createFromNamedSchemasForProfileOnly(
          final int maxFieldCount, final List<NamedSchema<?>> activeChildrenSchemas) {
    return new AbstractSszStableContainerSchema<>("", activeChildrenSchemas, maxFieldCount) {
      @Override
      public C createFromBackingNode(final TreeNode node) {
        throw new UnsupportedOperationException(
            "This stable container schema is meant to be used for creating a profile schema");
      }
    };
  }

  static <C extends SszStableContainer> SszStableContainerSchema<C> createFromSchemasForProfileOnly(
      final int maxFieldCount, final List<SszSchema<?>> activeChildrenSchemas) {
    return createFromNamedSchemasForProfileOnly(
        maxFieldCount,
        IntStream.range(0, activeChildrenSchemas.size())
            .<NamedSchema<?>>mapToObj(i -> namedSchema("field-" + i, activeChildrenSchemas.get(i)))
            .toList());
  }

  @Override
  default Optional<SszStableContainerSchema<?>> toStableContainerSchema() {
    return Optional.of(this);
  }

  @Override
  default SszStableContainerBaseSchema<?> toStableContainerSchemaBaseRequired() {
    return this;
  }
}
