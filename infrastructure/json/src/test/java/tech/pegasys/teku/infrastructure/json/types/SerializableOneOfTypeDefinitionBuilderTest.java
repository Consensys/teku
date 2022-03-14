/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.infrastructure.json.types;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.STRING_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.json.JsonTestUtil;
import tech.pegasys.teku.infrastructure.json.JsonUtil;

public class SerializableOneOfTypeDefinitionBuilderTest {
  private static final SerializableTypeDefinition<TestObjA> TYPE_A =
      SerializableTypeDefinition.object(TestObjA.class)
          .name("TA")
          .withField("value1", STRING_TYPE, TestType::getName)
          .build();
  private static final SerializableTypeDefinition<TestObjB> TYPE_B =
      SerializableTypeDefinition.object(TestObjB.class)
          .name("TB")
          .withField("value2", STRING_TYPE, TestType::getName)
          .build();

  SerializableOneOfTypeDefinition<TestType> definition =
      new SerializableOneOfDefinitionTypeBuilder<TestType>()
          .withType(TestObjA.class, TYPE_A)
          .withType(TestObjB.class, TYPE_B)
          .build();

  @Test
  void serialize_shouldWrite() throws JsonProcessingException {
    final String json = JsonUtil.serialize(new TestObjA("FOO"), definition);
    assertThat(json).isEqualTo("{\"value1\":\"FOO\"}");
  }

  @SuppressWarnings("unchecked")
  @Test
  void openapiType() throws Exception {
    final String json = JsonUtil.serialize(definition::serializeOpenApiTypeOrReference);
    final Map<String, Object> result = JsonTestUtil.parse(json);
    final List<Map<String, String>> oneOf = (ArrayList<Map<String, String>>) result.get("oneOf");

    final List<String> refs =
        oneOf.stream().map(element -> element.get("$ref")).collect(Collectors.toList());
    assertThat(refs).containsOnly("#/components/schemas/TA", "#/components/schemas/TB");
  }

  interface TestType {
    String getName();
  }

  static class TestObjA implements TestType {
    private final String name;

    public TestObjA(final String name) {
      this.name = name;
    }

    @Override
    public String getName() {
      return name;
    }
  }

  static class TestObjB extends TestObjA {
    public TestObjB(final String name) {
      super(name);
    }
  }
}
