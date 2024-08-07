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

import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.SszProfile;
import tech.pegasys.teku.infrastructure.ssz.SszStableContainer;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;

public interface SszProfileSchema<C extends SszProfile> extends SszStableContainerBaseSchema<C> {
  SszStableContainerSchema<? extends SszStableContainer> getStableContainerSchema();

  @Override
  default SszBitvectorSchema<SszBitvector> getActiveFieldsSchema() {
    return getStableContainerSchema().getActiveFieldsSchema();
  }

  @Override
  default SszStableContainerBaseSchema<?> toStableContainerSchemaBaseRequired() {
    return this;
  }

  @Override
  default Optional<SszProfileSchema<?>> toProfileSchema() {
    return Optional.of(this);
  }
}
