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
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.impl.AbstractSszImmutableContainer;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszStableContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszStableContainerSchema.NamedIndexedSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszStableProfileSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class AbstractSszStableContainerSchemaTest {

  static class StableContainer extends AbstractSszImmutableContainer {
    protected StableContainer(
        SszContainerSchema<? extends AbstractSszImmutableContainer> schema, TreeNode backingNode) {
      super(schema, backingNode);
    }
  }

  static class StableContainerSchema extends AbstractSszStableContainerSchema<StableContainer> {

    public StableContainerSchema(
        String name, List<NamedIndexedSchema<?>> childrenSchemas, int maxFieldCount) {
      super(name, childrenSchemas, maxFieldCount);
    }

    @Override
    public StableContainer createFromBackingNode(TreeNode node) {
      return new StableContainer(this, node);
    }
  }

  static class ProfileSchema extends AbstractSszStableProfileSchema<StableContainer> {

    public ProfileSchema(
        String name, List<NamedIndexedSchema<?>> childrenSchemas, int maxFieldCount) {
      super(name, childrenSchemas, maxFieldCount);
    }

    @Override
    public StableContainer createFromBackingNode(TreeNode node) {
      return new StableContainer(this, node);
    }
  }

  @Test
  void stableContainerSanityTest() {
    StableContainerSchema squareStableContainerSchema =
        new StableContainerSchema(
            "Square",
            List.of(
                new NamedIndexedSchema<>("side", 0, SszPrimitiveSchemas.UINT64_SCHEMA),
                new NamedIndexedSchema<>("color", 1, SszPrimitiveSchemas.UINT8_SCHEMA)),
            4);

    StableContainerSchema circleStableContainerSchema =
        new StableContainerSchema(
            "Circle",
            List.of(
                new NamedIndexedSchema<>("color", 1, SszPrimitiveSchemas.UINT8_SCHEMA),
                new NamedIndexedSchema<>("radius", 2, SszPrimitiveSchemas.UINT64_SCHEMA)),
            4);

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

    System.out.println(square.sszSerialize());
    System.out.println(circle.sszSerialize());
  }

  @Test
  void profileSanityTest() {
    ProfileSchema squareProfileSchema =
        new ProfileSchema(
            "Square",
            List.of(
                new NamedIndexedSchema<>("side", 0, SszPrimitiveSchemas.UINT64_SCHEMA),
                new NamedIndexedSchema<>("color", 1, SszPrimitiveSchemas.UINT8_SCHEMA)),
            4);

    ProfileSchema circleProfileSchema =
        new ProfileSchema(
            "Circle",
            List.of(
                new NamedIndexedSchema<>("color", 1, SszPrimitiveSchemas.UINT8_SCHEMA),
                new NamedIndexedSchema<>("radius", 2, SszPrimitiveSchemas.UINT64_SCHEMA)),
            4);

    StableContainer circleProfile =
        circleProfileSchema.createFromFieldValues(
            List.of(
                SszPrimitiveSchemas.UINT8_SCHEMA.boxed((byte) 1),
                SszUInt64.of(UInt64.valueOf(0x42))));

    StableContainer squareProfile =
        squareProfileSchema.createFromFieldValues(
            List.of(
                SszUInt64.of(UInt64.valueOf(0x42)),
                SszPrimitiveSchemas.UINT8_SCHEMA.boxed((byte) 1)));

    System.out.println(squareProfile.sszSerialize());
    System.out.println(circleProfile.sszSerialize());
  }
}
