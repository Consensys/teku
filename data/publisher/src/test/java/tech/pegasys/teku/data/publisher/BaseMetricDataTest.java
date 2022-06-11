/*
 * Copyright ConsenSys Software Inc., 2022
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.provider.JsonProvider;

public class BaseMetricDataTest {
  private final JsonProvider jsonProvider = new JsonProvider();

  @Test
  public void shouldSerializeObject() throws JsonProcessingException {
    final String processField = "system";
    final BaseMetricData process = new BaseMetricData(10L, processField);
    final String data = jsonProvider.objectToJSON(process);
    final ObjectMapper mapper = jsonProvider.getObjectMapper();
    final JsonNode node = mapper.readTree(data);
    assertThat(node.get("version").asInt()).isEqualTo(1);
    assertThat(node.get("process").asText()).isEqualTo(processField);
    assertThat(node.get("timestamp").asLong()).isEqualTo(10L);
  }
}
