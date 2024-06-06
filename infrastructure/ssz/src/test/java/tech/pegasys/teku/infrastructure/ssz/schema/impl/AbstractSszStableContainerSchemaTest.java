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
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.ssz.impl.SszStableContainerImpl;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszStableContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszContainerSchema.NamedSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class AbstractSszStableContainerSchemaTest {
  static final int maxFieldCount = 4;
  static final Map<Integer, NamedSchema<?>> squareSchemas =
      Map.of(
          0,
          namedSchema("side", SszPrimitiveSchemas.UINT64_SCHEMA),
          1,
          namedSchema("color", SszPrimitiveSchemas.UINT8_SCHEMA));

  static final Map<Integer, NamedSchema<?>> circleSchemas =
      Map.of(
          1,
          namedSchema("color", SszPrimitiveSchemas.UINT8_SCHEMA),
          2,
          namedSchema("radius", SszPrimitiveSchemas.UINT64_SCHEMA));

  static class StableContainer extends SszStableContainerImpl {

    public StableContainer(
        SszStableContainerSchema<? extends SszStableContainerImpl> type, TreeNode backingNode) {
      super(type, backingNode);
    }
  }

  static class StableContainerSchema extends AbstractSszStableContainerSchema<StableContainer> {

    public StableContainerSchema(
        String name, Map<Integer, NamedSchema<?>> childrenSchemas, int maxFieldCount) {
      super(name, childrenSchemas, maxFieldCount);
    }

    @Override
    public StableContainer createFromBackingNode(TreeNode node) {
      return new StableContainer(this, node);
    }
  }

  static class ProfileSchema extends AbstractSszStableProfileSchema<StableContainer> {

    public ProfileSchema(
        String name, Map<Integer, NamedSchema<?>> childrenSchemas, int maxFieldCount) {
      super(name, childrenSchemas, maxFieldCount);
    }

    @Override
    public StableContainer createFromBackingNode(TreeNode node) {
      return new StableContainer(this, node);
    }
  }

  @Test
  void stableContainerSanityTest() throws JsonProcessingException {
    StableContainerSchema squareStableContainerSchema =
        new StableContainerSchema("Square", squareSchemas, maxFieldCount);

    StableContainerSchema circleStableContainerSchema =
        new StableContainerSchema("Circle", circleSchemas, maxFieldCount);

    StableContainer square =
        squareStableContainerSchema.createFromFieldValues(
            List.of(
                SszUInt64.of(UInt64.valueOf(0x42)),
                SszPrimitiveSchemas.UINT8_SCHEMA.boxed((byte) 1)));

    StableContainer circle =
        circleStableContainerSchema.createFromFieldValues(
            List.of(
                SszPrimitiveSchemas.UINT8_SCHEMA.boxed((byte) 1),
                SszUInt64.of(UInt64.valueOf(0x42))));

    System.out.println("square sc serialization: " + square.sszSerialize());
    System.out.println("circle sc serialization: " + circle.sszSerialize());

    String squareJson =
        JsonUtil.serialize(square, squareStableContainerSchema.getJsonTypeDefinition());
    System.out.println("square sc json: " + squareJson);

    String circleJson =
        JsonUtil.serialize(circle, circleStableContainerSchema.getJsonTypeDefinition());
    System.out.println("circle sc json: " + circleJson);

    System.out.println("square sc root: " + square.hashTreeRoot());
    System.out.println("circle sc root: " + circle.hashTreeRoot());

    StableContainer deserializedCircle =
        circleStableContainerSchema.sszDeserialize(Bytes.fromHexString("0x06014200000000000000"));

    assertThat(deserializedCircle).isEqualTo(circle);
    assertThat(deserializedCircle.get(1))
        .isEqualTo(SszPrimitiveSchemas.UINT8_SCHEMA.boxed((byte) 1));
    assertThat(deserializedCircle.get(2)).isEqualTo(SszUInt64.of(UInt64.valueOf(0x42)));
    assertThatThrownBy(() -> deserializedCircle.get(0));

    StableContainer deserializedSquare =
        squareStableContainerSchema.sszDeserialize(Bytes.fromHexString("0x03420000000000000001"));

    assertThat(deserializedSquare).isEqualTo(square);
    assertThat(deserializedSquare.get(1))
        .isEqualTo(SszPrimitiveSchemas.UINT8_SCHEMA.boxed((byte) 1));
    assertThat(deserializedSquare.get(0)).isEqualTo(SszUInt64.of(UInt64.valueOf(0x42)));
    assertThatThrownBy(() -> deserializedSquare.get(2));
  }

  @Test
  void profileSanityTest() throws JsonProcessingException {
    ProfileSchema squareProfileSchema = new ProfileSchema("Square", squareSchemas, maxFieldCount);

    ProfileSchema circleProfileSchema = new ProfileSchema("Circle", circleSchemas, maxFieldCount);

    StableContainer circle =
        circleProfileSchema.createFromFieldValues(
            List.of(
                SszPrimitiveSchemas.UINT8_SCHEMA.boxed((byte) 1),
                SszUInt64.of(UInt64.valueOf(0x42))));

    StableContainer square =
        squareProfileSchema.createFromFieldValues(
            List.of(
                SszUInt64.of(UInt64.valueOf(0x42)),
                SszPrimitiveSchemas.UINT8_SCHEMA.boxed((byte) 1)));

    System.out.println("square profile serialization: " + square.sszSerialize());
    System.out.println("circle profile serialization: " + circle.sszSerialize());

    String squareJson = JsonUtil.serialize(square, squareProfileSchema.getJsonTypeDefinition());
    System.out.println("square profile json: " + squareJson);

    String circleJson = JsonUtil.serialize(circle, circleProfileSchema.getJsonTypeDefinition());
    System.out.println("circle profile json: " + circleJson);

    System.out.println("square profile root: " + square.hashTreeRoot());
    System.out.println("circle profile root: " + circle.hashTreeRoot());

    StableContainer deserializedCircle =
        circleProfileSchema.sszDeserialize(Bytes.fromHexString("0x014200000000000000"));

    assertThat(deserializedCircle).isEqualTo(circle);
    assertThat(deserializedCircle.get(1))
        .isEqualTo(SszPrimitiveSchemas.UINT8_SCHEMA.boxed((byte) 1));
    assertThat(deserializedCircle.get(2)).isEqualTo(SszUInt64.of(UInt64.valueOf(0x42)));
    assertThatThrownBy(() -> deserializedCircle.get(0));

    StableContainer deserializedSquare =
        squareProfileSchema.sszDeserialize(Bytes.fromHexString("0x420000000000000001"));

    assertThat(deserializedSquare).isEqualTo(square);
    assertThat(deserializedSquare.get(1))
        .isEqualTo(SszPrimitiveSchemas.UINT8_SCHEMA.boxed((byte) 1));
    assertThat(deserializedSquare.get(0)).isEqualTo(SszUInt64.of(UInt64.valueOf(0x42)));
    assertThatThrownBy(() -> deserializedSquare.get(2));
  }
}
