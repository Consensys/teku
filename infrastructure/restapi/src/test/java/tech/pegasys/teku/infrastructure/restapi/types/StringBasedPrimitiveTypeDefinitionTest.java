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
import static org.assertj.core.api.Assertions.entry;
import static tech.pegasys.teku.infrastructure.restapi.JsonTestUtil.parse;
import static tech.pegasys.teku.infrastructure.restapi.JsonTestUtil.parseString;
import static tech.pegasys.teku.infrastructure.restapi.json.JsonUtil.serialize;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.restapi.json.JsonUtil;

class StringBasedPrimitiveTypeDefinitionTest {
  @Test
  void serializeOpenApiType_withFormat() throws Exception {
    final StringBasedPrimitiveTypeDefinition<String> type =
        new StringBasedPrimitiveTypeDefinition<>(
            Function.identity(),
            Function.identity(),
            "ex",
            Optional.empty(),
            Optional.of("My format"));

    final Map<String, Object> result = parse(serialize(type::serializeOpenApiType));
    assertThat(result)
        .containsOnly(
            entry("type", "string"), entry("format", "My format"), entry("example", "ex"));
  }

  @Test
  void serializeOpenApiType_withoutFormat() throws Exception {
    final StringBasedPrimitiveTypeDefinition<String> type =
        new StringBasedPrimitiveTypeDefinition<>(
            Function.identity(),
            Function.identity(),
            "ex",
            Optional.of("A description"),
            Optional.empty());

    final Map<String, Object> result = parse(serialize(type::serializeOpenApiType));
    assertThat(result)
        .containsOnly(
            entry("type", "string"), entry("description", "A description"), entry("example", "ex"));
  }

  @Test
  void serializeOpenApiType_withDescription() throws Exception {
    final StringBasedPrimitiveTypeDefinition<String> type =
        new StringBasedPrimitiveTypeDefinition<>(
            Function.identity(),
            Function.identity(),
            "ex",
            Optional.of("A description"),
            Optional.empty());

    final Map<String, Object> result = parse(serialize(type::serializeOpenApiType));
    assertThat(result)
        .containsOnly(
            entry("type", "string"), entry("description", "A description"), entry("example", "ex"));
  }

  @Test
  void serializeOpenApiType_withoutDescription() throws Exception {
    final StringBasedPrimitiveTypeDefinition<String> type =
        new StringBasedPrimitiveTypeDefinition<>(
            Function.identity(),
            Function.identity(),
            "ex",
            Optional.empty(),
            Optional.of("my format"));

    final Map<String, Object> result = parse(serialize(type::serializeOpenApiType));
    assertThat(result)
        .containsOnly(
            entry("type", "string"), entry("format", "my format"), entry("example", "ex"));
  }

  @Test
  void serialize_shouldApplyConverter() throws Exception {
    final StringBasedPrimitiveTypeDefinition<String> type =
        new StringBasedPrimitiveTypeDefinition<>(
            value -> null,
            value -> value.toUpperCase(Locale.ROOT),
            "ex",
            Optional.of("description"),
            Optional.empty());

    final String result = parseString(serialize(gen -> type.serialize("Foo", gen)));

    assertThat(result).isEqualTo("FOO");
  }

  @Test
  void deserialize_shouldApplyConverter() throws Exception {
    final StringBasedPrimitiveTypeDefinition<String> type =
        new StringBasedPrimitiveTypeDefinition<>(
            value -> value.toUpperCase(Locale.ROOT),
            value -> null,
            "ex",
            Optional.of("description"),
            Optional.empty());

    final String result = JsonUtil.parse("\"Foo\"", type);

    assertThat(result).isEqualTo("FOO");
  }
}
