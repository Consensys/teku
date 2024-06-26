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
import java.util.Optional;
import java.util.Set;
import tech.pegasys.teku.infrastructure.ssz.impl.SszProfileImpl;
import tech.pegasys.teku.infrastructure.ssz.impl.SszStableContainerImpl;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszProfileSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszStableContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszProfileSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszStableContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszStableContainerSchema.NamedIndexedSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class TestStableContainers {

  static final int MAX_SHAPE_FIELD_COUNT = 4;

  static final List<NamedIndexedSchema<?>> SHAPE_SCHEMAS =
      List.of(
          namedIndexedSchema("side", 0, SszPrimitiveSchemas.UINT64_SCHEMA),
          namedIndexedSchema("color", 1, SszPrimitiveSchemas.UINT8_SCHEMA),
          namedIndexedSchema("radius", 2, SszPrimitiveSchemas.UINT64_SCHEMA));

  static final int SIDE_INDEX = 0;
  static final int COLOR_INDEX = 1;
  static final int RADIUS_INDEX = 2;

  static final Set<Integer> SQUARE_SCHEMA_INDICES = Set.of(SIDE_INDEX, COLOR_INDEX);

  static final Set<Integer> CIRCLE_SCHEMA_INDICES = Set.of(RADIUS_INDEX, COLOR_INDEX);

  public static class ShapeStableContainer extends SszStableContainerImpl {

    ShapeStableContainer(
        final SszStableContainerSchema<? extends SszStableContainerImpl> type,
        final TreeNode backingNode) {
      super(type, backingNode);
    }

    Optional<UInt64> getSide() {
      final Optional<SszUInt64> side = getAnyOptional(SIDE_INDEX);
      return side.map(SszUInt64::get);
    }

    Optional<Byte> getColor() {
      final Optional<SszByte> color = getAnyOptional(COLOR_INDEX);
      return color.map(SszByte::get);
    }

    Optional<UInt64> getRadius() {
      final Optional<SszUInt64> radius = getAnyOptional(RADIUS_INDEX);
      return radius.map(SszUInt64::get);
    }
  }

  public static class CircleProfile extends SszProfileImpl {
    CircleProfile(
        final SszProfileSchema<? extends SszProfileImpl> type, final TreeNode backingNode) {
      super(type, backingNode);
    }

    Byte getColor() {
      final SszByte color = getAny(COLOR_INDEX);
      return color.get();
    }

    UInt64 getRadius() {
      final SszUInt64 radius = getAny(RADIUS_INDEX);
      return radius.get();
    }
  }

  public static class SquareProfile extends SszProfileImpl {
    SquareProfile(
        final SszProfileSchema<? extends SszProfileImpl> type, final TreeNode backingNode) {
      super(type, backingNode);
    }

    Byte getColor() {
      final SszByte color = getAny(COLOR_INDEX);
      return color.get();
    }

    UInt64 getSide() {
      final SszUInt64 side = getAny(SIDE_INDEX);
      return side.get();
    }
  }

  static class StableContainerSchema
      extends AbstractSszStableContainerSchema<ShapeStableContainer> {

    public StableContainerSchema(
        final String name,
        final List<NamedIndexedSchema<?>> childrenSchemas,
        final int maxFieldCount) {
      super(name, childrenSchemas, maxFieldCount);
    }

    @Override
    public ShapeStableContainer createFromBackingNode(final TreeNode node) {
      return new ShapeStableContainer(this, node);
    }
  }

  public static final SszStableContainerSchema<ShapeStableContainer> SHAPE_STABLE_CONTAINER_SCHEMA =
      new AbstractSszStableContainerSchema<>("Shape", SHAPE_SCHEMAS, MAX_SHAPE_FIELD_COUNT) {
        @Override
        public ShapeStableContainer createFromBackingNode(final TreeNode node) {
          return new ShapeStableContainer(this, node);
        }
      };

  public static final SszProfileSchema<CircleProfile> CIRCLE_PROFILE_SCHEMA =
      new AbstractSszProfileSchema<>(
          "CircleProfile", SHAPE_STABLE_CONTAINER_SCHEMA, CIRCLE_SCHEMA_INDICES) {
        @Override
        public CircleProfile createFromBackingNode(final TreeNode node) {
          return new CircleProfile(this, node);
        }
      };

  public static final SszProfileSchema<SquareProfile> SQUARE_PROFILE_SCHEMA =
      new AbstractSszProfileSchema<>(
          "SquareProfile", SHAPE_STABLE_CONTAINER_SCHEMA, SQUARE_SCHEMA_INDICES) {
        @Override
        public SquareProfile createFromBackingNode(final TreeNode node) {
          return new SquareProfile(this, node);
        }
      };
}
