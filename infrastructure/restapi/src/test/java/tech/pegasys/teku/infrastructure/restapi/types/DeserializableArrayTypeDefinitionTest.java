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
import tech.pegasys.teku.infrastructure.restapi.json.JsonUtil;

class DeserializableArrayTypeDefinitionTest {

  private final DeserializableTypeDefinition<List<String>> stringListType =
      DeserializableTypeDefinition.listOf(STRING_TYPE);

  @Test
  void shouldRoundTripEmptyList() throws Exception {
    final List<String> result =
        JsonUtil.parse(JsonUtil.serialize(emptyList(), stringListType), stringListType);

    assertThat(result).isEmpty();
  }

  @Test
  void shouldRoundTripNonEmptyList() throws Exception {
    final List<String> value = List.of("x", "y", "z", "x");
    final List<String> result =
        JsonUtil.parse(JsonUtil.serialize(value, stringListType), stringListType);

    assertThat(result).isEqualTo(value);
  }
}
