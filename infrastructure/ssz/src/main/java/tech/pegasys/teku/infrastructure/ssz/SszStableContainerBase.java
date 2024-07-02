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

package tech.pegasys.teku.infrastructure.ssz;

import java.util.NoSuchElementException;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;

public interface SszStableContainerBase extends SszContainer {
  boolean isFieldActive(int index);

  SszBitvector getActiveFields();

  default Optional<SszData> getOptional(final int index) {
    try {
      return Optional.of(get(index));
    } catch (final NoSuchElementException __) {
      return Optional.empty();
    }
  }

  @SuppressWarnings({"unchecked", "TypeParameterUnusedInFormals"})
  // container is heterogeneous by its nature so making unsafe cast here
  // is more convenient and is not less safe
  default <C extends SszData> Optional<C> getAnyOptional(final int index) {
    return (Optional<C>) getOptional(index);
  }
}
