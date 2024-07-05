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
import static tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszContainerSchema.namedSchema;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszProfile;
import tech.pegasys.teku.infrastructure.ssz.SszStableContainer;
import tech.pegasys.teku.infrastructure.ssz.SszStableContainerBase;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
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
import tech.pegasys.teku.infrastructure.ssz.sos.SszReader;
import tech.pegasys.teku.infrastructure.ssz.sos.SszWriter;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class AbstractSszStableContainerBaseSchemaTest {
  static final int MAX_SHAPE_FIELD_COUNT = 5;

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
  void stableContainerSanityTest() throws JsonProcessingException {

    final ShapeStableContainer square =
        SHAPE_STABLE_CONTAINER_SCHEMA.createFromOptionalFieldValues(
            List.of(
                Optional.of(SszUInt64.of(UInt64.valueOf(0x42))),
                Optional.of(SszPrimitiveSchemas.UINT8_SCHEMA.boxed((byte) 1))));

    assertSquare(square, (byte) 1, UInt64.valueOf(0x42));
    assertThat(square.hashTreeRoot())
        .isEqualTo(
            Bytes32.fromHexString(
                "0xddc7acd38ae9d6d6788c14bd7635aeb1d7694768d7e00e1795bb6d328ec14f28"));
    assertThat(square.sszSerialize()).isEqualTo(Bytes.fromHexString("0x03420000000000000001"));

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
                "0xe823471310312d52aa1135d971a3ed72ba041ade3ec5b5077c17a39d73ab17c5"));
    assertThat(circle.sszSerialize()).isEqualTo(Bytes.fromHexString("0x06014200000000000000"));

    // json square round trip
    final String squareJson =
        JsonUtil.serialize(square, SHAPE_STABLE_CONTAINER_SCHEMA.getJsonTypeDefinition());

    final ShapeStableContainer squareFromJson =
        JsonUtil.parse(squareJson, SHAPE_STABLE_CONTAINER_SCHEMA.getJsonTypeDefinition());
    assertThat(squareFromJson).isEqualTo(square);

    // json circle round trip
    final String circleJson =
        JsonUtil.serialize(circle, SHAPE_STABLE_CONTAINER_SCHEMA.getJsonTypeDefinition());

    final ShapeStableContainer circleFromJson =
        JsonUtil.parse(circleJson, SHAPE_STABLE_CONTAINER_SCHEMA.getJsonTypeDefinition());
    assertThat(circleFromJson).isEqualTo(circle);

    // ssz circle deserialization

    ShapeStableContainer deserializedCircle =
        SHAPE_STABLE_CONTAINER_SCHEMA.sszDeserialize(Bytes.fromHexString("0x06014200000000000000"));

    assertCircle(circle, (byte) 1, UInt64.valueOf(0x42));
    assertThat(deserializedCircle).isEqualTo(circle);

    // ssz square deserialization
    ShapeStableContainer deserializedSquare =
        SHAPE_STABLE_CONTAINER_SCHEMA.sszDeserialize(Bytes.fromHexString("0x03420000000000000001"));

    assertSquare(square, (byte) 1, UInt64.valueOf(0x42));
    assertThat(deserializedSquare).isEqualTo(square);
  }

  @Test
  void profileSanityTest() throws JsonProcessingException {

    CircleProfile circle =
        CIRCLE_PROFILE_SCHEMA.createFromFieldValues(
            List.of(
                SszPrimitiveSchemas.UINT8_SCHEMA.boxed((byte) 1),
                SszUInt64.of(UInt64.valueOf(0x42))));

    assertCircleProfile(circle, (byte) 1, UInt64.valueOf(0x42), Optional.empty());
    assertThat(circle.hashTreeRoot())
        .isEqualTo(
            Bytes32.fromHexString(
                "0xe823471310312d52aa1135d971a3ed72ba041ade3ec5b5077c17a39d73ab17c5"));
    assertThat(circle.sszSerialize()).isEqualTo(Bytes.fromHexString("0x014200000000000000"));

    SquareProfile square =
        SQUARE_PROFILE_SCHEMA.createFromFieldValues(
            List.of(
                SszUInt64.of(UInt64.valueOf(0x42)),
                SszPrimitiveSchemas.UINT8_SCHEMA.boxed((byte) 1)));

    assertSquareProfile(square, (byte) 1, UInt64.valueOf(0x42), Optional.empty());
    assertThat(square.hashTreeRoot())
        .isEqualTo(
            Bytes32.fromHexString(
                "0xddc7acd38ae9d6d6788c14bd7635aeb1d7694768d7e00e1795bb6d328ec14f28"));
    assertThat(square.sszSerialize()).isEqualTo(Bytes.fromHexString("0x420000000000000001"));

    // json square round trip
    final String squareJson =
        JsonUtil.serialize(square, SQUARE_PROFILE_SCHEMA.getJsonTypeDefinition());

    System.out.println("squareJson: " + squareJson);

    final SquareProfile squareFromJson =
        JsonUtil.parse(squareJson, SQUARE_PROFILE_SCHEMA.getJsonTypeDefinition());
    assertThat(squareFromJson).isEqualTo(square);

    // json circle round trip
    final String circleJson =
        JsonUtil.serialize(circle, CIRCLE_PROFILE_SCHEMA.getJsonTypeDefinition());

    System.out.println("circleJson: " + circleJson);

    final CircleProfile circleFromJson =
        JsonUtil.parse(circleJson, CIRCLE_PROFILE_SCHEMA.getJsonTypeDefinition());
    assertThat(circleFromJson).isEqualTo(circle);

    CircleProfile deserializedCircle =
        CIRCLE_PROFILE_SCHEMA.sszDeserialize(Bytes.fromHexString("0x014200000000000000"));

    assertCircleProfile(circle, (byte) 1, UInt64.valueOf(0x42), Optional.empty());
    assertThat(deserializedCircle).isEqualTo(circle);

    SquareProfile deserializedSquare =
        SQUARE_PROFILE_SCHEMA.sszDeserialize(Bytes.fromHexString("0x420000000000000001"));

    assertSquareProfile(square, (byte) 1, UInt64.valueOf(0x42), Optional.empty());
    assertThat(deserializedSquare).isEqualTo(square);
  }

  @Test
  void profileWithOptionalSanityTest() throws JsonProcessingException {

    CircleProfile circle =
        CIRCLE_PROFILE_WITH_OPTIONAL_SCHEMA.createFromOptionalFieldValues(
            List.of(
                Optional.empty(),
                Optional.of(SszPrimitiveSchemas.UINT8_SCHEMA.boxed((byte) 1)),
                Optional.of(SszUInt64.of(UInt64.valueOf(0x42)))));

    assertCircleProfile(circle, (byte) 1, UInt64.valueOf(0x42), Optional.empty());
    assertThat(circle.hashTreeRoot())
        .isEqualTo(
            Bytes32.fromHexString(
                "0xe823471310312d52aa1135d971a3ed72ba041ade3ec5b5077c17a39d73ab17c5"));
    assertThat(circle.sszSerialize()).isEqualTo(Bytes.fromHexString("0x00014200000000000000"));

    CircleProfile circleWithOptional =
        CIRCLE_PROFILE_WITH_OPTIONAL_SCHEMA.createFromOptionalFieldValues(
            List.of(
                Optional.empty(),
                Optional.of(SszPrimitiveSchemas.UINT8_SCHEMA.boxed((byte) 1)),
                Optional.of(SszUInt64.of(UInt64.valueOf(0x42))),
                Optional.of(SszPrimitiveSchemas.UINT8_SCHEMA.boxed((byte) 3))));

    assertCircleProfile(circleWithOptional, (byte) 1, UInt64.valueOf(0x42), Optional.of((byte) 3));

    assertThat(circleWithOptional.hashTreeRoot())
        .isEqualTo(
            Bytes32.fromHexString(
                "0xc34a2669febfec69fd04f51698339b183cba43548c17303e1dd88c4bf451504c"));
    assertThat(circleWithOptional.sszSerialize())
        .isEqualTo(Bytes.fromHexString("0x0101420000000000000003"));

    SquareProfile squareWithOptional =
        SQUARE_PROFILE_WITH_OPTIONAL_SCHEMA.createFromOptionalFieldValues(
            List.of(
                Optional.of(SszUInt64.of(UInt64.valueOf(0x42))),
                Optional.of(SszPrimitiveSchemas.UINT8_SCHEMA.boxed((byte) 1)),
                Optional.empty(),
                Optional.of(SszPrimitiveSchemas.UINT8_SCHEMA.boxed((byte) 3))));

    assertSquareProfile(squareWithOptional, (byte) 1, UInt64.valueOf(0x42), Optional.of((byte) 3));
    assertThat(squareWithOptional.hashTreeRoot())
        .isEqualTo(
            Bytes32.fromHexString(
                "0x763802b1b20e709f08faf13ce94b32f49fd69f218e2e5ee4a3c2dfed62121ec1"));
    assertThat(squareWithOptional.sszSerialize())
        .isEqualTo(Bytes.fromHexString("0x0142000000000000000103"));

    // json squareWithOptional round trip
    final String squareJson =
        JsonUtil.serialize(
            squareWithOptional, SQUARE_PROFILE_WITH_OPTIONAL_SCHEMA.getJsonTypeDefinition());

    final SquareProfile squareFromJson =
        JsonUtil.parse(squareJson, SQUARE_PROFILE_WITH_OPTIONAL_SCHEMA.getJsonTypeDefinition());
    assertThat(squareFromJson).isEqualTo(squareWithOptional);

    // json circle round trip
    final String circleJson =
        JsonUtil.serialize(circle, CIRCLE_PROFILE_WITH_OPTIONAL_SCHEMA.getJsonTypeDefinition());

    final CircleProfile circleFromJson =
        JsonUtil.parse(circleJson, CIRCLE_PROFILE_WITH_OPTIONAL_SCHEMA.getJsonTypeDefinition());
    assertThat(circleFromJson).isEqualTo(circle);

    CircleProfile deserializedCircle =
        CIRCLE_PROFILE_WITH_OPTIONAL_SCHEMA.sszDeserialize(
            Bytes.fromHexString("0x00014200000000000000"));

    assertCircleProfile(deserializedCircle, (byte) 1, UInt64.valueOf(0x42), Optional.empty());
    assertThat(deserializedCircle).isEqualTo(circle);

    SquareProfile deserializedSquare =
        SQUARE_PROFILE_WITH_OPTIONAL_SCHEMA.sszDeserialize(
            Bytes.fromHexString("0x0142000000000000000103"));

    assertSquareProfile(squareWithOptional, (byte) 1, UInt64.valueOf(0x42), Optional.of((byte) 3));
    assertThat(deserializedSquare).isEqualTo(squareWithOptional);
  }

  @Test
  void computeSszLengthBounds() {
    final AbstractSszStableContainerBaseSchema<SszStableContainerBase>
        variableProfileWithOptionals =
            new AbstractSszStableContainerBaseSchema<>(
                "Test",
                List.of(
                    namedSchema("side", SszPrimitiveSchemas.UINT64_SCHEMA),
                    namedSchema("color", SszPrimitiveSchemas.UINT8_SCHEMA),
                    namedSchema(
                        "description", SszListSchema.create(SszPrimitiveSchemas.BYTE_SCHEMA, 32)),
                    namedSchema("radius", SszPrimitiveSchemas.UINT64_SCHEMA),
                    namedSchema("style", SszPrimitiveSchemas.UINT8_SCHEMA),
                    namedSchema(
                        "description2", SszListSchema.create(SszPrimitiveSchemas.BYTE_SCHEMA, 32))),
                Set.of(0, 1, 2),
                Set.of(4, 5),
                10) {
              @Override
              int sszSerializeActiveFields(SszBitvector activeFieldsBitvector, SszWriter writer) {
                throw new UnsupportedOperationException();
              }

              @Override
              SszBitvector sszDeserializeActiveFieldsTree(SszReader reader) {
                throw new UnsupportedOperationException();
              }

              @Override
              public SszStableContainerBase createFromBackingNode(final TreeNode node) {
                throw new UnsupportedOperationException();
              }
            };

    final SszLengthBounds bounds = variableProfileWithOptionals.getSszLengthBounds();
    assertThat(bounds.getMinBytes()).isEqualTo(13);
    assertThat(bounds.getMaxBytes()).isEqualTo(82);
  }

  @Test
  void shouldThrowOutOfBoundsException() {
    CircleProfile circle =
        CIRCLE_PROFILE_WITH_OPTIONAL_SCHEMA.createFromOptionalFieldValues(
            List.of(
                Optional.empty(),
                Optional.of(SszPrimitiveSchemas.UINT8_SCHEMA.boxed((byte) 1)),
                Optional.of(SszUInt64.of(UInt64.valueOf(0x42)))));

    assertThatThrownBy(() -> circle.get(MAX_SHAPE_FIELD_COUNT))
        .isInstanceOf(IndexOutOfBoundsException.class);
  }

  private void assertSquare(
      final SszStableContainer container, final byte color, final UInt64 side) {
    assertOptionalFieldValue(
        container, COLOR_INDEX, Optional.of(SszPrimitiveSchemas.UINT8_SCHEMA.boxed(color)));
    assertOptionalFieldValue(container, SIDE_INDEX, Optional.of(SszUInt64.of(side)));
    assertOptionalFieldValue(container, RADIUS_INDEX, Optional.empty());
  }

  private void assertCircle(
      final SszStableContainer container, final byte color, final UInt64 radius) {
    assertOptionalFieldValue(
        container, COLOR_INDEX, Optional.of(SszPrimitiveSchemas.UINT8_SCHEMA.boxed(color)));
    assertOptionalFieldValue(container, RADIUS_INDEX, Optional.of(SszUInt64.of(radius)));
    assertOptionalFieldValue(container, SIDE_INDEX, Optional.empty());
  }

  private void assertSquareProfile(
      final SszProfile container, final byte color, final UInt64 side, final Optional<Byte> style) {
    assertRequiredFieldValue(
        container, COLOR_INDEX, Optional.of(SszPrimitiveSchemas.UINT8_SCHEMA.boxed(color)));
    assertRequiredFieldValue(container, SIDE_INDEX, Optional.of(SszUInt64.of(side)));
    assertRequiredFieldValue(container, RADIUS_INDEX, Optional.empty());

    assertOptionalFieldValue(
        container, STYLE_INDEX, style.map(SszPrimitiveSchemas.UINT8_SCHEMA::boxed));
  }

  private void assertCircleProfile(
      final SszProfile container,
      final byte color,
      final UInt64 radius,
      final Optional<Byte> style) {
    assertRequiredFieldValue(
        container, COLOR_INDEX, Optional.of(SszPrimitiveSchemas.UINT8_SCHEMA.boxed(color)));
    assertRequiredFieldValue(container, RADIUS_INDEX, Optional.of(SszUInt64.of(radius)));
    assertRequiredFieldValue(container, SIDE_INDEX, Optional.empty());

    assertOptionalFieldValue(
        container, STYLE_INDEX, style.map(SszPrimitiveSchemas.UINT8_SCHEMA::boxed));
  }

  private void assertOptionalFieldValue(
      final SszStableContainerBase container,
      final int fieldIndex,
      final Optional<? extends SszData> value) {
    value.ifPresentOrElse(
        sszData -> assertThat(container.get(fieldIndex)).isEqualTo(sszData),
        () ->
            assertThatThrownBy(() -> container.get(fieldIndex))
                .isInstanceOf(NoSuchElementException.class));

    assertThat(container.getOptional(fieldIndex)).isEqualTo(value);
  }

  private void assertRequiredFieldValue(
      final SszProfile container, final int fieldIndex, final Optional<? extends SszData> value) {
    value.ifPresentOrElse(
        sszData -> assertThat(container.get(fieldIndex)).isEqualTo(sszData),
        () ->
            assertThatThrownBy(() -> container.get(fieldIndex))
                .isInstanceOf(NoSuchElementException.class));
  }
}
