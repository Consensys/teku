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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.restapi.json.JsonUtil;

public class EnumTypeDefinitionTest {
  DeserializableTypeDefinition<YesNo> definition = DeserializableTypeDefinition.enumOf(YesNo.class);

  @Test
  void shouldSerializeEnum() throws Exception {
    final String json = JsonUtil.serialize(YesNo.YES, definition);
    assertThat(json).isEqualTo("\"yes\"");
  }

  @Test
  void shouldParseEnum() throws Exception {
    assertThat(JsonUtil.parse("\"no\"", definition)).isEqualTo(YesNo.NO);
  }

  private enum YesNo {
    YES("yes"),
    NO("no");

    private final String displayName;

    YesNo(final String displayName) {
      this.displayName = displayName;
    }

    @Override
    public String toString() {
      return displayName;
    }
  }
}
