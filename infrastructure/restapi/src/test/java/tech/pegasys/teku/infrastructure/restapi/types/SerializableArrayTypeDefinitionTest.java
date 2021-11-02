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

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.restapi.types.CoreTypes.STRING_TYPE;

import java.util.List;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.restapi.JsonTestUtil;
import tech.pegasys.teku.infrastructure.restapi.json.JsonUtil;

public class SerializableArrayTypeDefinitionTest {
  private final SerializableTypeDefinition<List<String>> stringListType =
      SerializableTypeDefinition.listOf(STRING_TYPE);

  @Test
  void serialize_shouldSerializeEmptyArray() throws Exception {
    final List<Object> result =
        JsonTestUtil.parseList(JsonUtil.serialize(emptyList(), stringListType));
    assertThat(result).isEmpty();
  }

  @Test
  void serialize_shouldSerializeArrayWithValues() throws Exception {
    final List<Object> result =
        JsonTestUtil.parseList(JsonUtil.serialize(List.of("a", "b", "c"), stringListType));
    assertThat(result).containsExactly("a", "b", "c");
  }

  @Test
  void shouldGetReferencedTypesRecursively() {
    final SerializableTypeDefinition<String> type1 =
        SerializableTypeDefinition.object(String.class).name("Type1").build();
    final SerializableTypeDefinition<String> type2 =
        SerializableTypeDefinition.object(String.class)
            .name("Type2")
            .withField("type1", type1, __ -> null)
            .build();

    assertThat(SerializableTypeDefinition.listOf(type2).getReferencedTypeDefinitions())
        .containsExactlyInAnyOrder(type1, type2);
  }
}
