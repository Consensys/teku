/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.beaconrestapi.v1.beacon;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.List;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetExecutionPayloadEnvelope;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

public class GetExecutionPayloadEnvelopeIntegrationTest
    extends AbstractDataBackedRestAPIIntegrationTest {

  @Test
  public void shouldGetExecutionPayloadEnvelopeAtTheHeadOfTheChain() throws IOException {
    startRestAPIAtGenesis(SpecMilestone.GLOAS);
    final List<SignedBlockAndState> created = createBlocksAtSlots(10);

    final Response response = get("head");
    final JsonNode body = OBJECT_MAPPER.readTree(response.body().string());

    assertThat(body.get("version").asText()).isEqualTo("gloas");
    assertThat(body.get("execution_optimistic").asBoolean()).isFalse();
    assertThat(body.get("finalized").asBoolean()).isFalse();
    assertThat(response.header(HEADER_CONSENSUS_VERSION)).isEqualTo("gloas");

    final SignedExecutionPayloadEnvelope executionPayloadEnvelope =
        getSignedExecutionPayloadEnvelope(SpecMilestone.GLOAS, body.get("data").toString());

    assertThat(executionPayloadEnvelope.getBeaconBlockRoot())
        .isEqualTo(created.getLast().getBlock().getRoot());
    assertThat(executionPayloadEnvelope)
        .isEqualTo(
            chainBuilder.getExecutionPayload(created.getLast().getBlock().getRoot()).orElseThrow());
  }

  private SignedExecutionPayloadEnvelope getSignedExecutionPayloadEnvelope(
      final SpecMilestone milestone, final String data) throws JsonProcessingException {
    System.out.println(data);
    return JsonUtil.parse(
        data,
        SchemaDefinitionsGloas.required(spec.forMilestone(milestone).getSchemaDefinitions())
            .getSignedExecutionPayloadEnvelopeSchema()
            .getJsonTypeDefinition());
  }

  private Response get(final String blockIdString) throws IOException {
    return getResponse(GetExecutionPayloadEnvelope.ROUTE.replace("{block_id}", blockIdString));
  }
}
