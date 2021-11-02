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
import static tech.pegasys.teku.infrastructure.restapi.types.CommonTypeDefinitions.STRING_TYPE;
import static tech.pegasys.teku.infrastructure.restapi.types.CommonTypeDefinitions.UINT64_TYPE;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.restapi.JsonTestUtil;
import tech.pegasys.teku.infrastructure.restapi.json.JsonUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class SerializableObjectTypeDefinitionBuilderTest {

  @Test
  void shouldSerializeSimpleValue() throws Exception {
    final SerializableTypeDefinition<SimpleValue> type =
        SerializableTypeDefinition.object(SimpleValue.class)
            .withField("value1", STRING_TYPE, SimpleValue::getValue1)
            .withField("value2", UINT64_TYPE, SimpleValue::getValue2)
            .build();

    final String json = JsonUtil.serialize(new SimpleValue("abc", UInt64.valueOf(300)), type);
    assertThat(JsonTestUtil.parse(json))
        .containsExactly(entry("value1", "abc"), entry("value2", "300"));
  }

  private static class SimpleValue {
    private final String value1;
    private final UInt64 value2;

    private SimpleValue(final String value1, final UInt64 value2) {
      this.value1 = value1;
      this.value2 = value2;
    }

    public String getValue1() {
      return value1;
    }

    public UInt64 getValue2() {
      return value2;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final SimpleValue that = (SimpleValue) o;
      return Objects.equals(value1, that.value1) && Objects.equals(value2, that.value2);
    }

    @Override
    public int hashCode() {
      return Objects.hash(value1, value2);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("value1", value1)
          .add("value2", value2)
          .toString();
    }
  }
}
