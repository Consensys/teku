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

package tech.pegasys.teku.infrastructure.ssz.schema.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.infrastructure.ssz.schema.SszStableContainerSchema.createFromNamedSchemasForProfileOnly;
import static tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszContainerSchema.namedSchema;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.json.JsonTestUtil;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszProfile;
import tech.pegasys.teku.infrastructure.ssz.SszStableContainer;
import tech.pegasys.teku.infrastructure.ssz.SszStableContainerBase;
import tech.pegasys.teku.infrastructure.ssz.impl.SszProfileImpl;
import tech.pegasys.teku.infrastructure.ssz.impl.SszStableContainerImpl;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszProfileSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszStableContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszContainerSchema.NamedSchema;
import tech.pegasys.teku.infrastructure.ssz.sos.SszLengthBounds;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class AbstractSszStableContainerBaseSchemaTest {
  static final int MAX_SHAPE_FIELD_COUNT = 16;

  static final List<NamedSchema<?>> SHAPE_SCHEMAS =
      List.of(
          namedSchema("side", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("color", SszPrimitiveSchemas.UINT8_SCHEMA),
          namedSchema("radius", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("style", SszPrimitiveSchemas.UINT8_SCHEMA));

  static final int SIDE_INDEX = 0;
  static final int COLOR_INDEX = 1;
  static final int RADIUS_INDEX = 2;
  static final int STYLE_INDEX = 3;

  static final Set<Integer> SQUARE_SCHEMA_INDICES = Set.of(SIDE_INDEX, COLOR_INDEX);

  static final Set<Integer> CIRCLE_SCHEMA_INDICES = Set.of(RADIUS_INDEX, COLOR_INDEX);

  static class ShapeStableContainer extends SszStableContainerImpl {

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

  static class CircleProfile extends SszProfileImpl {
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

  static class SquareProfile extends SszProfileImpl {
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

  private static final SszStableContainerSchema<ShapeStableContainer>
      SHAPE_STABLE_CONTAINER_SCHEMA =
          new AbstractSszStableContainerSchema<>("Shape", SHAPE_SCHEMAS, MAX_SHAPE_FIELD_COUNT) {
            @Override
            public ShapeStableContainer createFromBackingNode(final TreeNode node) {
              return new ShapeStableContainer(this, node);
            }
          };

  private static final SszProfileSchema<CircleProfile> CIRCLE_PROFILE_SCHEMA =
      new AbstractSszProfileSchema<>(
          "Circle", SHAPE_STABLE_CONTAINER_SCHEMA, CIRCLE_SCHEMA_INDICES, Set.of()) {
        @Override
        public CircleProfile createFromBackingNode(final TreeNode node) {
          return new CircleProfile(this, node);
        }
      };

  private static final SszProfileSchema<SquareProfile> SQUARE_PROFILE_SCHEMA =
      new AbstractSszProfileSchema<>(
          "Square", SHAPE_STABLE_CONTAINER_SCHEMA, SQUARE_SCHEMA_INDICES, Set.of()) {
        @Override
        public SquareProfile createFromBackingNode(final TreeNode node) {
          return new SquareProfile(this, node);
        }
      };

  private static final SszProfileSchema<CircleProfile> CIRCLE_PROFILE_WITH_OPTIONAL_SCHEMA =
      new AbstractSszProfileSchema<>(
          "Circle", SHAPE_STABLE_CONTAINER_SCHEMA, CIRCLE_SCHEMA_INDICES, Set.of(STYLE_INDEX)) {
        @Override
        public CircleProfile createFromBackingNode(final TreeNode node) {
          return new CircleProfile(this, node);
        }
      };

  private static final SszProfileSchema<SquareProfile> SQUARE_PROFILE_WITH_OPTIONAL_SCHEMA =
      new AbstractSszProfileSchema<>(
          "Square", SHAPE_STABLE_CONTAINER_SCHEMA, SQUARE_SCHEMA_INDICES, Set.of(STYLE_INDEX)) {
        @Override
        public SquareProfile createFromBackingNode(final TreeNode node) {
          return new SquareProfile(this, node);
        }
      };

  @Test
  void stableContainerSanityTest() throws Exception {

    final ShapeStableContainer square =
        SHAPE_STABLE_CONTAINER_SCHEMA.createFromOptionalFieldValues(
            List.of(
                Optional.of(SszUInt64.of(UInt64.valueOf(0x42))),
                Optional.of(SszPrimitiveSchemas.UINT8_SCHEMA.boxed((byte) 1))));

    assertSquare(square, (byte) 1, UInt64.valueOf(0x42));
    assertThat(square.hashTreeRoot())
        .isEqualTo(
            Bytes32.fromHexString(
                "0x676ebb1aa0a62edeae70c7c1e36be52910662cf662eead3ef524ffdfe7a61c59"));
    assertThat(square.sszSerialize()).isEqualTo(Bytes.fromHexString("0x0300420000000000000001"));

    final ShapeStableContainer circle =
        SHAPE_STABLE_CONTAINER_SCHEMA.createFromOptionalFieldValues(
            List.of(
                Optional.empty(),
                Optional.of(SszPrimitiveSchemas.UINT8_SCHEMA.boxed((byte) 1)),
                Optional.of(SszUInt64.of(UInt64.valueOf(0x42)))));

    assertCircle(circle, (byte) 1, UInt64.valueOf(0x42));
    assertThat(circle.hashTreeRoot())
        .isEqualTo(
            Bytes32.fromHexString(
                "0x905b9b66f05a75db8441d55fe82b081f00a306467b526f83a4222fa0642211bc"));
    assertThat(circle.sszSerialize()).isEqualTo(Bytes.fromHexString("0x0600014200000000000000"));

    // json square round trip
    final String squareJson =
        JsonUtil.serialize(square, SHAPE_STABLE_CONTAINER_SCHEMA.getJsonTypeDefinition());

    assertThat(JsonTestUtil.parse(squareJson)).containsOnlyKeys("side", "color");

    final ShapeStableContainer squareFromJson =
        JsonUtil.parse(squareJson, SHAPE_STABLE_CONTAINER_SCHEMA.getJsonTypeDefinition());
    assertThat(squareFromJson).isEqualTo(square);

    // json circle round trip
    final String circleJson =
        JsonUtil.serialize(circle, SHAPE_STABLE_CONTAINER_SCHEMA.getJsonTypeDefinition());

    assertThat(JsonTestUtil.parse(circleJson)).containsOnlyKeys("radius", "color");

    final ShapeStableContainer circleFromJson =
        JsonUtil.parse(circleJson, SHAPE_STABLE_CONTAINER_SCHEMA.getJsonTypeDefinition());
    assertThat(circleFromJson).isEqualTo(circle);

    // ssz circle deserialization

    ShapeStableContainer deserializedCircle =
        SHAPE_STABLE_CONTAINER_SCHEMA.sszDeserialize(
            Bytes.fromHexString("0x0600014200000000000000"));

    assertCircle(circle, (byte) 1, UInt64.valueOf(0x42));
    assertThat(deserializedCircle).isEqualTo(circle);

    // ssz square deserialization
    ShapeStableContainer deserializedSquare =
        SHAPE_STABLE_CONTAINER_SCHEMA.sszDeserialize(
            Bytes.fromHexString("0x0300420000000000000001"));

    assertSquare(square, (byte) 1, UInt64.valueOf(0x42));
    assertThat(deserializedSquare).isEqualTo(square);
  }

  @Test
  void profileSanityTest() throws Exception {

    final CircleProfile circle =
        CIRCLE_PROFILE_SCHEMA.createFromFieldValues(
            List.of(
                SszPrimitiveSchemas.UINT8_SCHEMA.boxed((byte) 1),
                SszUInt64.of(UInt64.valueOf(0x42))));

    assertCircleProfile(circle, (byte) 1, UInt64.valueOf(0x42), Optional.empty(), false);
    assertThat(circle.hashTreeRoot())
        .isEqualTo(
            Bytes32.fromHexString(
                "0x905b9b66f05a75db8441d55fe82b081f00a306467b526f83a4222fa0642211bc"));
    assertThat(circle.sszSerialize()).isEqualTo(Bytes.fromHexString("0x014200000000000000"));

    final SquareProfile square =
        SQUARE_PROFILE_SCHEMA.createFromFieldValues(
            List.of(
                SszUInt64.of(UInt64.valueOf(0x42)),
                SszPrimitiveSchemas.UINT8_SCHEMA.boxed((byte) 1)));

    assertSquareProfile(square, (byte) 1, UInt64.valueOf(0x42), Optional.empty(), false);
    assertThat(square.hashTreeRoot())
        .isEqualTo(
            Bytes32.fromHexString(
                "0x676ebb1aa0a62edeae70c7c1e36be52910662cf662eead3ef524ffdfe7a61c59"));
    assertThat(square.sszSerialize()).isEqualTo(Bytes.fromHexString("0x420000000000000001"));

    // json square round trip
    final String squareJson =
        JsonUtil.serialize(square, SQUARE_PROFILE_SCHEMA.getJsonTypeDefinition());

    assertThat(JsonTestUtil.parse(squareJson)).containsOnlyKeys("side", "color");

    final SquareProfile squareFromJson =
        JsonUtil.parse(squareJson, SQUARE_PROFILE_SCHEMA.getJsonTypeDefinition());
    assertThat(squareFromJson).isEqualTo(square);

    // json circle round trip
    final String circleJson =
        JsonUtil.serialize(circle, CIRCLE_PROFILE_SCHEMA.getJsonTypeDefinition());

    assertThat(JsonTestUtil.parse(circleJson)).containsOnlyKeys("radius", "color");

    final CircleProfile circleFromJson =
        JsonUtil.parse(circleJson, CIRCLE_PROFILE_SCHEMA.getJsonTypeDefinition());
    assertThat(circleFromJson).isEqualTo(circle);

    final CircleProfile deserializedCircle =
        CIRCLE_PROFILE_SCHEMA.sszDeserialize(Bytes.fromHexString("0x014200000000000000"));

    assertCircleProfile(circle, (byte) 1, UInt64.valueOf(0x42), Optional.empty(), false);
    assertThat(deserializedCircle).isEqualTo(circle);

    final SquareProfile deserializedSquare =
        SQUARE_PROFILE_SCHEMA.sszDeserialize(Bytes.fromHexString("0x420000000000000001"));

    assertSquareProfile(square, (byte) 1, UInt64.valueOf(0x42), Optional.empty(), false);
    assertThat(deserializedSquare).isEqualTo(square);
  }

  @Test
  void profileWithOptionalSanityTest() throws Exception {

    final CircleProfile circle =
        CIRCLE_PROFILE_WITH_OPTIONAL_SCHEMA.createFromOptionalFieldValues(
            List.of(
                Optional.empty(),
                Optional.of(SszPrimitiveSchemas.UINT8_SCHEMA.boxed((byte) 1)),
                Optional.of(SszUInt64.of(UInt64.valueOf(0x42)))));

    assertCircleProfile(circle, (byte) 1, UInt64.valueOf(0x42), Optional.empty(), true);
    assertThat(circle.hashTreeRoot())
        .isEqualTo(
            Bytes32.fromHexString(
                "0x905b9b66f05a75db8441d55fe82b081f00a306467b526f83a4222fa0642211bc"));
    assertThat(circle.sszSerialize()).isEqualTo(Bytes.fromHexString("0x00014200000000000000"));

    final CircleProfile circleWithOptional =
        CIRCLE_PROFILE_WITH_OPTIONAL_SCHEMA.createFromOptionalFieldValues(
            List.of(
                Optional.empty(),
                Optional.of(SszPrimitiveSchemas.UINT8_SCHEMA.boxed((byte) 1)),
                Optional.of(SszUInt64.of(UInt64.valueOf(0x42))),
                Optional.of(SszPrimitiveSchemas.UINT8_SCHEMA.boxed((byte) 3))));

    assertCircleProfile(
        circleWithOptional, (byte) 1, UInt64.valueOf(0x42), Optional.of((byte) 3), true);

    assertThat(circleWithOptional.hashTreeRoot())
        .isEqualTo(
            Bytes32.fromHexString(
                "0x969d1bbccfad8aa59dc9f7c0a4a79d80d6dc76ad0b88224ca413983b81eed232"));
    assertThat(circleWithOptional.sszSerialize())
        .isEqualTo(Bytes.fromHexString("0x0101420000000000000003"));

    final SquareProfile squareWithOptional =
        SQUARE_PROFILE_WITH_OPTIONAL_SCHEMA.createFromOptionalFieldValues(
            List.of(
                Optional.of(SszUInt64.of(UInt64.valueOf(0x42))),
                Optional.of(SszPrimitiveSchemas.UINT8_SCHEMA.boxed((byte) 1)),
                Optional.empty(),
                Optional.of(SszPrimitiveSchemas.UINT8_SCHEMA.boxed((byte) 3))));

    assertSquareProfile(
        squareWithOptional, (byte) 1, UInt64.valueOf(0x42), Optional.of((byte) 3), true);
    assertThat(squareWithOptional.hashTreeRoot())
        .isEqualTo(
            Bytes32.fromHexString(
                "0x162a5167b484695d5e2d18a5674c6d654650572542e058072431575ff5592db0"));
    assertThat(squareWithOptional.sszSerialize())
        .isEqualTo(Bytes.fromHexString("0x0142000000000000000103"));

    // json squareWithOptional round trip
    final String squareJson =
        JsonUtil.serialize(
            squareWithOptional, SQUARE_PROFILE_WITH_OPTIONAL_SCHEMA.getJsonTypeDefinition());

    assertThat(JsonTestUtil.parse(squareJson)).containsOnlyKeys("side", "color", "style");

    final SquareProfile squareFromJson =
        JsonUtil.parse(squareJson, SQUARE_PROFILE_WITH_OPTIONAL_SCHEMA.getJsonTypeDefinition());
    assertThat(squareFromJson).isEqualTo(squareWithOptional);

    // json circle round trip (optional not set)
    final String circleJson =
        JsonUtil.serialize(circle, CIRCLE_PROFILE_WITH_OPTIONAL_SCHEMA.getJsonTypeDefinition());

    assertThat(JsonTestUtil.parse(circleJson)).containsOnlyKeys("radius", "color");

    final CircleProfile circleFromJson =
        JsonUtil.parse(circleJson, CIRCLE_PROFILE_WITH_OPTIONAL_SCHEMA.getJsonTypeDefinition());
    assertThat(circleFromJson).isEqualTo(circle);

    final CircleProfile deserializedCircle =
        CIRCLE_PROFILE_WITH_OPTIONAL_SCHEMA.sszDeserialize(
            Bytes.fromHexString("0x00014200000000000000"));

    assertCircleProfile(deserializedCircle, (byte) 1, UInt64.valueOf(0x42), Optional.empty(), true);
    assertThat(deserializedCircle).isEqualTo(circle);

    final SquareProfile deserializedSquare =
        SQUARE_PROFILE_WITH_OPTIONAL_SCHEMA.sszDeserialize(
            Bytes.fromHexString("0x0142000000000000000103"));

    assertSquareProfile(
        squareWithOptional, (byte) 1, UInt64.valueOf(0x42), Optional.of((byte) 3), true);
    assertThat(deserializedSquare).isEqualTo(squareWithOptional);
  }

  @Test
  void computeSszLengthBounds() {
    final SszStableContainerSchema<SszStableContainer> stableContainer =
        createFromNamedSchemasForProfileOnly(
            48,
            List.of(
                namedSchema("side", SszPrimitiveSchemas.UINT64_SCHEMA),
                namedSchema("color", SszPrimitiveSchemas.UINT8_SCHEMA),
                namedSchema(
                    "description", SszListSchema.create(SszPrimitiveSchemas.BYTE_SCHEMA, 32)),
                namedSchema("radius", SszPrimitiveSchemas.UINT64_SCHEMA),
                namedSchema("style", SszPrimitiveSchemas.UINT8_SCHEMA),
                namedSchema(
                    "description2", SszListSchema.create(SszPrimitiveSchemas.BYTE_SCHEMA, 32))));

    final SszLengthBounds boundsWithOptionals =
        getSszLengthBoundsForProfile(stableContainer, Set.of(0, 1, 2), Set.of(4, 5));
    assertThat(boundsWithOptionals.getMinBytes()).isEqualTo(14);
    assertThat(boundsWithOptionals.getMaxBytes()).isEqualTo(83);

    final SszLengthBounds boundsWithoutOptionals =
        getSszLengthBoundsForProfile(stableContainer, Set.of(0, 1, 2), Set.of());
    assertThat(boundsWithoutOptionals.getMinBytes()).isEqualTo(13);
    assertThat(boundsWithoutOptionals.getMaxBytes()).isEqualTo(45);

    final SszLengthBounds stableContainerBounds = stableContainer.getSszLengthBounds();
    assertThat(stableContainerBounds.getMinBytes()).isEqualTo(6);
    assertThat(stableContainerBounds.getMaxBytes()).isEqualTo(96);
  }

  private SszLengthBounds getSszLengthBoundsForProfile(
      final SszStableContainerSchema<?> stableContainer,
      final Set<Integer> requiredFieldIndices,
      final Set<Integer> optionalFieldIndices) {

    return new AbstractSszProfileSchema<>(
        "Test", stableContainer, requiredFieldIndices, optionalFieldIndices) {

      @Override
      public SszProfileImpl createFromBackingNode(final TreeNode node) {
        throw new UnsupportedOperationException();
      }
    }.getSszLengthBounds();
  }

  @Test
  void shouldThrowOutOfBoundsException() {
    final CircleProfile circle =
        CIRCLE_PROFILE_WITH_OPTIONAL_SCHEMA.createFromOptionalFieldValues(
            List.of(
                Optional.empty(),
                Optional.of(SszPrimitiveSchemas.UINT8_SCHEMA.boxed((byte) 1)),
                Optional.of(SszUInt64.of(UInt64.valueOf(0x42)))));

    assertThatThrownBy(() -> circle.get(MAX_SHAPE_FIELD_COUNT))
        .isInstanceOf(IndexOutOfBoundsException.class);

    final ShapeStableContainer square =
        SHAPE_STABLE_CONTAINER_SCHEMA.createFromOptionalFieldValues(
            List.of(
                Optional.of(SszUInt64.of(UInt64.valueOf(0x42))),
                Optional.of(SszPrimitiveSchemas.UINT8_SCHEMA.boxed((byte) 1))));

    assertThatThrownBy(() -> square.get(MAX_SHAPE_FIELD_COUNT))
        .isInstanceOf(IndexOutOfBoundsException.class);
  }

  private void assertSquare(
      final SszStableContainer container, final byte color, final UInt64 side) {
    assertOptionalFieldValue(
        container, COLOR_INDEX, Optional.of(SszPrimitiveSchemas.UINT8_SCHEMA.boxed(color)), true);
    assertOptionalFieldValue(container, SIDE_INDEX, Optional.of(SszUInt64.of(side)), true);
    assertOptionalFieldValue(container, RADIUS_INDEX, Optional.empty(), true);
  }

  private void assertCircle(
      final SszStableContainer container, final byte color, final UInt64 radius) {
    assertOptionalFieldValue(
        container, COLOR_INDEX, Optional.of(SszPrimitiveSchemas.UINT8_SCHEMA.boxed(color)), true);
    assertOptionalFieldValue(container, RADIUS_INDEX, Optional.of(SszUInt64.of(radius)), true);
    assertOptionalFieldValue(container, SIDE_INDEX, Optional.empty(), true);
  }

  private void assertSquareProfile(
      final SszProfile container,
      final byte color,
      final UInt64 side,
      final Optional<Byte> style,
      final boolean styleAllowed) {
    assertFixedFieldValue(
        container, COLOR_INDEX, Optional.of(SszPrimitiveSchemas.UINT8_SCHEMA.boxed(color)));
    assertFixedFieldValue(container, SIDE_INDEX, Optional.of(SszUInt64.of(side)));
    assertFixedFieldValue(container, RADIUS_INDEX, Optional.empty());

    assertOptionalFieldValue(
        container, STYLE_INDEX, style.map(SszPrimitiveSchemas.UINT8_SCHEMA::boxed), styleAllowed);
  }

  private void assertCircleProfile(
      final SszProfile container,
      final byte color,
      final UInt64 radius,
      final Optional<Byte> style,
      final boolean styleAllowed) {
    assertFixedFieldValue(
        container, COLOR_INDEX, Optional.of(SszPrimitiveSchemas.UINT8_SCHEMA.boxed(color)));
    assertFixedFieldValue(container, RADIUS_INDEX, Optional.of(SszUInt64.of(radius)));
    assertFixedFieldValue(container, SIDE_INDEX, Optional.empty());

    assertOptionalFieldValue(
        container, STYLE_INDEX, style.map(SszPrimitiveSchemas.UINT8_SCHEMA::boxed), styleAllowed);
  }

  private void assertOptionalFieldValue(
      final SszStableContainerBase container,
      final int fieldIndex,
      final Optional<? extends SszData> value,
      final boolean isAllowed) {
    if (isAllowed) {
      value.ifPresentOrElse(
          sszData -> assertThat(container.get(fieldIndex)).isEqualTo(sszData),
          () ->
              assertThatThrownBy(() -> container.get(fieldIndex))
                  .isInstanceOf(NoSuchElementException.class));

      assertThat(container.getOptional(fieldIndex)).isEqualTo(value);
    } else {

      assertThatThrownBy(() -> container.getOptional(fieldIndex))
          .isInstanceOf(IndexOutOfBoundsException.class);
      assertThatThrownBy(() -> container.get(fieldIndex))
          .isInstanceOf(IndexOutOfBoundsException.class);
    }
  }

  private void assertFixedFieldValue(
      final SszProfile container, final int fieldIndex, final Optional<? extends SszData> value) {
    value.ifPresentOrElse(
        sszData -> {
          assertThat(container.get(fieldIndex)).isEqualTo(sszData);
          assertThat(container.getOptional(fieldIndex)).isEqualTo(value);
        },
        () -> {
          assertThatThrownBy(() -> container.get(fieldIndex))
              .isInstanceOf(IndexOutOfBoundsException.class);
          assertThatThrownBy(() -> container.getOptional(fieldIndex))
              .isInstanceOf(IndexOutOfBoundsException.class);
        });
  }
}
