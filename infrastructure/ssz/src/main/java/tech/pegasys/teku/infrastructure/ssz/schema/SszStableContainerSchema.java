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

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszStableContainer;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszStableContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszStableContainerSchema.NamedIndexedSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public interface SszStableContainerSchema<C extends SszStableContainer>
    extends SszContainerSchema<C> {

  /**
   * Creates a new {@link SszStableContainer} schema with specified field schemas and container
   * instance constructor
   */
  static <C extends SszStableContainer> SszStableContainerSchema<C> create(
      final String name,
      final List<NamedIndexedSchema<?>> activeChildrenSchemas,
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
   * be used in profile schema creation only. There will be actual ssz type for it.
   */
  static <C extends SszStableContainer> SszStableContainerSchema<C> createForProfileOnly(
      final List<NamedIndexedSchema<?>> activeChildrenSchemas, final int maxFieldCount) {
    return new AbstractSszStableContainerSchema<>("", activeChildrenSchemas, maxFieldCount) {
      @Override
      public C createFromBackingNode(final TreeNode node) {
        throw new UnsupportedOperationException(
            "This stable container schema is meant to be used for creating a profile schema");
      }
    };
  }

  List<? extends NamedIndexedSchema<?>> getDefinedChildrenSchemas();

  int getMaxFieldCount();

  SszBitvectorSchema<SszBitvector> getActiveFieldsSchema();

  TreeNode createTreeFromOptionalFieldValues(List<Optional<? extends SszData>> fieldValues);

  default C createFromOptionalFieldValues(final List<Optional<? extends SszData>> fieldValues) {
    return createFromBackingNode(createTreeFromOptionalFieldValues(fieldValues));
  }

  SszBitvector getDefaultActiveFields();

  SszBitvector getActiveFieldsBitvectorFromBackingNode(TreeNode node);

  @Override
  @SuppressWarnings("unchecked")
  default Optional<SszStableContainerSchema<?>> toStableContainerSchema() {
    return Optional.of(this);
  }
}
