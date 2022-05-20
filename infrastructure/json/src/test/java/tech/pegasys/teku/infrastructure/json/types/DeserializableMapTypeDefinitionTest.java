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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.json.JsonUtil;

public class DeserializableMapTypeDefinitionTest {
  private final Map<String, String> data = new TreeMap<>();

  @BeforeEach
  void setup() {
    data.put("ak", "av");
    data.put("bk", "bv");
    data.put("ck", "cv");
  }

  final String serializedData = "{\"ak\":\"av\",\"bk\":\"bv\",\"ck\":\"cv\"}";
  final DeserializableMapTypeDefinition<String, String> definition =
      new DeserializableMapTypeDefinition<>(
          CoreTypes.STRING_TYPE,
          CoreTypes.STRING_TYPE,
          TreeMap::new,
          Optional.empty(),
          Optional.empty(),
          Optional.empty());

  @Test
  void shouldSerializeMap() throws JsonProcessingException {
    assertThat(JsonUtil.serialize(data, definition)).isEqualTo(serializedData);
  }

  @Test
  void shouldDeserializeMap() throws JsonProcessingException {
    assertThat(JsonUtil.parse(serializedData, definition)).isEqualTo(data);
  }

  @Test
  void shouldRejectNestedArray() {
    final String input = "{\"ak\":[1,2,3]}";
    assertThatThrownBy(() -> JsonUtil.parse(input, definition))
        .isInstanceOf(MismatchedInputException.class);
  }

  @Test
  void shouldRejectNestedObject() {
    final String input = "{\"ak\":{\"bk\":1}}";
    assertThatThrownBy(() -> JsonUtil.parse(input, definition))
        .isInstanceOf(MismatchedInputException.class);
  }

  @Test
  void shouldWriteOpenApiType() throws JsonProcessingException {
    final String json = JsonUtil.serialize(definition::serializeOpenApiType);
    assertThat(json)
        .isEqualTo("{\"type\":\"object\",\"additionalProperties\":{\"type\":\"string\"}}");
  }
}
