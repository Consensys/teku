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

import static tech.pegasys.teku.infrastructure.ssz.TestProfiles.CIRCLE_PROFILE_SCHEMA;
import static tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszContainerSchema.namedSchema;

import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.TestContainers.TestContainer;
import tech.pegasys.teku.infrastructure.ssz.impl.SszStableContainerImpl;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszStableContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszContainerSchema.NamedSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszStableContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class TestStableContainers {

  static final int MAX_SHAPE_FIELD_COUNT = 4;

  static final List<NamedSchema<?>> SHAPE_SCHEMAS =
      List.of(
          namedSchema("side", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("color", SszPrimitiveSchemas.BYTE_SCHEMA),
          namedSchema("radius", SszPrimitiveSchemas.UINT64_SCHEMA));

  static final int SIDE_INDEX = 0;
  static final int COLOR_INDEX = 1;
  static final int RADIUS_INDEX = 2;

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

  public static class NestedStableContainer extends SszStableContainerImpl {
    NestedStableContainer(
        final SszStableContainerSchema<? extends SszStableContainerImpl> type,
        final TreeNode backingNode) {
      super(type, backingNode);
    }
  }

  public static final SszStableContainerSchema<ShapeStableContainer> SHAPE_STABLE_CONTAINER_SCHEMA =
      new AbstractSszStableContainerSchema<>("Shape", SHAPE_SCHEMAS, MAX_SHAPE_FIELD_COUNT) {
        @Override
        public ShapeStableContainer createFromBackingNode(final TreeNode node) {
          return new ShapeStableContainer(this, node);
        }
      };

  static final List<NamedSchema<?>> NESTED_SCHEMAS =
      List.of(
          namedSchema("bytevector", SszVectorSchema.create(SszPrimitiveSchemas.BYTE_SCHEMA, 64)),
          namedSchema("shapeStableContainer", SHAPE_STABLE_CONTAINER_SCHEMA),
          namedSchema("testContainer", TestContainer.SSZ_SCHEMA));

  public static final SszStableContainerSchema<ShapeStableContainer>
      NESTED_STABLE_CONTAINER_SCHEMA =
          new AbstractSszStableContainerSchema<>("NestedStableContainer", NESTED_SCHEMAS, 8) {
            @Override
            public ShapeStableContainer createFromBackingNode(final TreeNode node) {
              return new ShapeStableContainer(this, node);
            }
          };

  static final List<NamedSchema<?>> PROFILE_NESTED_SCHEMAS =
          List.of(
                  namedSchema("bytevector", SszVectorSchema.create(SszPrimitiveSchemas.BYTE_SCHEMA, 64)),
                  namedSchema("circleProfile", CIRCLE_PROFILE_SCHEMA),
                  namedSchema("testContainer", TestContainer.SSZ_SCHEMA));

  public static final SszStableContainerSchema<ShapeStableContainer>
          PROFILE_NESTED_STABLE_CONTAINER_SCHEMA =
          new AbstractSszStableContainerSchema<>("ProfileNestedStableContainer", PROFILE_NESTED_SCHEMAS, 8) {
            @Override
            public ShapeStableContainer createFromBackingNode(final TreeNode node) {
              return new ShapeStableContainer(this, node);
            }
          };
}
