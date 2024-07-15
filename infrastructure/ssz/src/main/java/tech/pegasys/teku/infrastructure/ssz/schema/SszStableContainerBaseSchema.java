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
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszStableContainerBase;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszContainerSchema.NamedSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

/**
 * {@link SszSchema} representing base functionalities that the Profile and StableContainer schemas
 * have in common as per <a href="https://eips.ethereum.org/EIPS/eip-7495">eip-7495</a>
 * specifications. In particular, they both have:
 *
 * <ol>
 *   <li>A theoretical future maximum field count.
 *   <li>A bitvector schema representing which fields are active (as a required field or as an
 *       optional field).
 *   <li>Due to possible field optionality, they can both be created from a list of optional {@link
 *       SszData}.
 * </ol>
 *
 * @param <C> the type of actual container class
 */
public interface SszStableContainerBaseSchema<C extends SszStableContainerBase>
    extends SszContainerSchema<C> {

  /**
   * The potential maximum number of fields to which the container can ever grow in the future.
   *
   * @return stable container maximum field count.
   */
  int getMaxFieldCount();

  List<NamedSchema<?>> getChildrenNamedSchemas();

  SszBitvectorSchema<SszBitvector> getActiveFieldsSchema();

  TreeNode createTreeFromOptionalFieldValues(List<Optional<? extends SszData>> fieldValues);

  default C createFromOptionalFieldValues(final List<Optional<? extends SszData>> fieldValues) {
    return createFromBackingNode(createTreeFromOptionalFieldValues(fieldValues));
  }

  SszBitvector getRequiredFields();

  SszBitvector getOptionalFields();

  default boolean isFieldAllowed(final int index) {
    if (index >= getMaxFieldCount()) {
      return false;
    }
    return getRequiredFields().getBit(index) || getOptionalFields().getBit(index);
  }

  default boolean hasOptionalFields() {
    return getOptionalFields().getBitCount() > 0;
  }

  SszBitvector getActiveFieldsBitvectorFromBackingNode(TreeNode node);
}
