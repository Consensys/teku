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

package tech.pegasys.teku.infrastructure.restapi.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenApiDocBuilderTest {

  @Test
  void shouldBuildValidDocWithMinimalInfo() throws Exception {
    final String json = validBuilder().build();
    final Map<String, Object> result = parse(json);

    assertThat(result).containsEntry("openapi", OpenApiDocBuilder.OPENAPI_VERSION);
    assertThat(getObject(result, "info"))
        .containsExactly(entry("title", "My Title"), entry("version", "My Version"));
  }

  @Test
  void shouldFailIfTitleNotSupplied() {
    assertThatThrownBy(new OpenApiDocBuilder().version("version")::build)
        .isInstanceOf(NullPointerException.class)
        .hasMessage("title must be supplied");
  }

  @Test
  void shouldFailIfVersionNotSupplied() {
    assertThatThrownBy(new OpenApiDocBuilder().title("title")::build)
        .isInstanceOf(NullPointerException.class)
        .hasMessage("version must be supplied");
  }

  @Test
  void shouldIncludeLicense() throws Exception {
    final Map<String, Object> result = parse(validBuilder().license("foo", "bar").build());

    assertThat(getObject(result, "info", "license"))
        .containsExactly(entry("name", "foo"), entry("url", "bar"));
  }

  @Test
  void shouldIncludeDescription() throws Exception {
    final Map<String, Object> result =
        parse(validBuilder().description("Some description").build());
    assertThat(getObject(result, "info")).containsEntry("description", "Some description");
  }

  private OpenApiDocBuilder validBuilder() {
    return new OpenApiDocBuilder().title("My Title").version("My Version");
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> getObject(final Map<String, Object> input, final String... names) {
    Map<String, Object> current = input;
    for (String name : names) {
      assertThat(current).containsKey(name);
      current = (Map<String, Object>) current.get(name);
    }
    return current;
  }

  private Map<String, Object> parse(final String json) throws Exception {
    return new ObjectMapper()
        .readerFor(
            TypeFactory.defaultInstance()
                .constructMapType(LinkedHashMap.class, String.class, Object.class))
        .readValue(json);
  }
}
