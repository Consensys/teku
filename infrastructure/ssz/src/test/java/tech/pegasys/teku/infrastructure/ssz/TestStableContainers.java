/*
 * Copyright Consensys Software Inc., 2022
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

import static tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszStableContainerSchema.namedIndexedSchema;

import java.util.List;
import tech.pegasys.teku.infrastructure.ssz.impl.SszProfileImpl;
import tech.pegasys.teku.infrastructure.ssz.impl.SszStableContainerImpl;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszProfileSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszStableContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszStableContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszStableContainerSchema.NamedIndexedSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszStableProfileSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class TestStableContainers {

  private static final int SHAPE_MAX_FIELD_COUNT = 4;
  private static final NamedIndexedSchema<?> SIDE_SCHEMA =
      namedIndexedSchema("side", 0, SszPrimitiveSchemas.UINT64_SCHEMA);
  private static final NamedIndexedSchema<?> COLOR_SCHEMA =
      namedIndexedSchema("color", 1, SszPrimitiveSchemas.UINT8_SCHEMA);
  private static final NamedIndexedSchema<?> RADIUS_SCHEMA =
      namedIndexedSchema("radius", 2, SszPrimitiveSchemas.UINT64_SCHEMA);

  static class StableContainer extends SszStableContainerImpl {

    StableContainer(
        final SszStableContainerSchema<? extends SszStableContainerImpl> type,
        final TreeNode backingNode) {
      super(type, backingNode);
    }
  }

  static class Profile extends SszProfileImpl {

    Profile(final SszProfileSchema<? extends SszProfileImpl> type, final TreeNode backingNode) {
      super(type, backingNode);
    }
  }

  static class StableContainerSchema extends AbstractSszStableContainerSchema<StableContainer> {

    public StableContainerSchema(
        final String name,
        final List<NamedIndexedSchema<?>> childrenSchemas,
        final int maxFieldCount) {
      super(name, childrenSchemas, maxFieldCount);
    }

    @Override
    public StableContainer createFromBackingNode(final TreeNode node) {
      return new StableContainer(this, node);
    }
  }

  static class ProfileSchema extends AbstractSszStableProfileSchema<Profile> {

    public ProfileSchema(
        final String name,
        final List<NamedIndexedSchema<?>> childrenSchemas,
        final int maxFieldCount) {
      super(name, childrenSchemas, maxFieldCount);
    }

    @Override
    public Profile createFromBackingNode(final TreeNode node) {
      return new Profile(this, node);
    }
  }

  private static final List<NamedIndexedSchema<?>> SQUARE_SCHEMAS =
      List.of(SIDE_SCHEMA, COLOR_SCHEMA);

  private static final List<NamedIndexedSchema<?>> CIRCLE_SCHEMAS =
      List.of(COLOR_SCHEMA, RADIUS_SCHEMA);

  public static final StableContainerSchema SQUARE_STABLE_CONTAINER_SCHEMA =
      new StableContainerSchema("Square", SQUARE_SCHEMAS, SHAPE_MAX_FIELD_COUNT);

  public static final StableContainerSchema CIRCLE_STABLE_CONTAINER_SCHEMA =
      new StableContainerSchema("Circle", CIRCLE_SCHEMAS, SHAPE_MAX_FIELD_COUNT);

  public static final ProfileSchema SQUARE_PROFILE_SCHEMA =
      new ProfileSchema("Square", SQUARE_SCHEMAS, SHAPE_MAX_FIELD_COUNT);

  public static final ProfileSchema CIRCLE_PROFILE_SCHEMA =
      new ProfileSchema("Circle", CIRCLE_SCHEMAS, SHAPE_MAX_FIELD_COUNT);
}
