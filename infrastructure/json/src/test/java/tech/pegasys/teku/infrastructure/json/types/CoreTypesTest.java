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

package tech.pegasys.teku.infrastructure.json.types;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static tech.pegasys.teku.infrastructure.json.DeserializableTypeUtil.assertRoundTrip;
import static tech.pegasys.teku.infrastructure.json.JsonUtil.serialize;

import java.util.Map;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.http.HttpErrorResponse;
import tech.pegasys.teku.infrastructure.json.JsonTestUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class CoreTypesTest {
  @Test
  void uint64_shouldRoundTrip() throws Exception {
    assertRoundTrip(UInt64.valueOf(200), CoreTypes.UINT64_TYPE);
  }

  @Test
  void string_shouldRoundTrip() throws Exception {
    assertRoundTrip("some string", CoreTypes.STRING_TYPE);
  }

  @Test
  void integer_shouldRoundTrip() throws Exception {
    assertRoundTrip(458, CoreTypes.INTEGER_TYPE);
  }

  @Test
  void httpErrorResponse_shouldSerialize() throws Exception {
    final HttpErrorResponse value = new HttpErrorResponse(442, "No good");
    final Map<String, Object> result =
        JsonTestUtil.parse(serialize(value, CoreTypes.HTTP_ERROR_RESPONSE_TYPE));

    assertThat(result).containsOnly(entry("status", 442), entry("message", "No good"));
  }
}
