/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.infrastructure.restapi.types;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.restapi.types.PrimitiveTypeDefinition.STRING_TYPE;
import static tech.pegasys.teku.infrastructure.restapi.types.PrimitiveTypeDefinition.UINT64_TYPE;

import com.google.common.base.MoreObjects;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.restapi.MagicSerializerThing;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class TypeDefinitionTest {

  @Test
  void shouldCreateSimpleDefinition() throws Exception {
    final TypeDefinition<SimpleType> typeDefinition =
        TypeDefinition.object(SimpleTypeBuilder::new, "SimpleType")
            .withField(
                "field1",
                "description1",
                STRING_TYPE,
                SimpleType::getField1,
                SimpleTypeBuilder::field1)
            .withField(
                "field2",
                "description2",
                UINT64_TYPE,
                SimpleType::getField2,
                SimpleTypeBuilder::field2)
            .withField(
                "field3",
                "description3",
                TypeDefinition.listOf(STRING_TYPE),
                SimpleType::getField3,
                SimpleTypeBuilder::field3)
            .build();

    final SimpleType value = new SimpleType("abc", UInt64.valueOf(3300), List.of("a", "b", "c"));

    final MagicSerializerThing magicSerializerThing = new MagicSerializerThing();
    final String json = magicSerializerThing.serialize(typeDefinition, value);
    System.out.println("JSON: " + json);
    final SimpleType result = magicSerializerThing.deserialize(typeDefinition, json);
    assertThat(result).isEqualTo(value);
  }

  private static class SimpleType {
    private final String field1;
    private final UInt64 field2;
    private final List<String> field3;

    private SimpleType(final String field1, final UInt64 field3, final List<String> field4) {
      this.field1 = field1;
      this.field2 = field3;
      this.field3 = field4;
    }

    public String getField1() {
      return field1;
    }

    public UInt64 getField2() {
      return field2;
    }

    public List<String> getField3() {
      return field3;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final SimpleType that = (SimpleType) o;
      return Objects.equals(field1, that.field1)
          && Objects.equals(field2, that.field2)
          && Objects.equals(field3, that.field3);
    }

    @Override
    public int hashCode() {
      return Objects.hash(field1, field2, field3);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("field1", field1)
          .add("field2", field2)
          .add("field3", field3)
          .toString();
    }
  }

  private static class SimpleTypeBuilder implements InternalTypeBuilder<SimpleType> {
    private String field1;
    private UInt64 field2;
    private List<String> field3;

    public SimpleTypeBuilder field1(final String field1) {
      this.field1 = field1;
      return this;
    }

    public SimpleTypeBuilder field2(final UInt64 field3) {
      this.field2 = field3;
      return this;
    }

    public SimpleTypeBuilder field3(final List<String> field4) {
      this.field3 = field4;
      return this;
    }

    @Override
    public SimpleType build() {
      return new SimpleType(field1, field2, field3);
    }
  }
}
