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

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.restapi.json.JsonUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class PrimitiveTypesTest {
  @Test
  void uint64_shouldRoundTrip() throws Exception {
    assertRoundTrip(UInt64.valueOf(200), PrimitiveTypes.UINT64_TYPE);
  }

  @Test
  void string_shouldRoundTrip() throws Exception {
    assertRoundTrip("some string", PrimitiveTypes.STRING_TYPE);
  }

  private <T> void assertRoundTrip(final T value, final TwoWayTypeDefinition<T> type)
      throws JsonProcessingException {
    final T result = JsonUtil.parse(JsonUtil.serialize(value, type), type);
    assertThat(result).isEqualTo(value);
  }
}
