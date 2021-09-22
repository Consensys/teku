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

package tech.pegasys.teku.data.publisher;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;

class ValidatorMetricDataTest {

  private final JsonProvider jsonProvider = new JsonProvider();

  @Test
  public void shouldSerializeObject() throws JsonProcessingException {
    final ValidatorMetricData process =
        new ValidatorMetricData(
            1, UInt64.valueOf(10L).longValue(), "system", 11L, 12L, "teku", "21.8", 3, 4);
    final String data = jsonProvider.objectToJSON(process);
    assertThat(process).isEqualTo(jsonProvider.jsonToObject(data, ValidatorMetricData.class));
  }
}
