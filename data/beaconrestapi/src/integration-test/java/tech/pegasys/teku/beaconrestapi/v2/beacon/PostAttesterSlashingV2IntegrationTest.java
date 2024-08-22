/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.beaconrestapi.v2.beacon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.spec.SpecMilestone.ELECTRA;
import static tech.pegasys.teku.spec.SpecMilestone.PHASE0;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collections;
import java.util.Locale;
import java.util.Optional;
import okhttp3.Response;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.api.schema.AttesterSlashing;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v2.beacon.PostAttesterSlashingV2;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.JsonTestUtil;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

@TestSpecContext(milestone = {PHASE0, ELECTRA})
public class PostAttesterSlashingV2IntegrationTest
    extends AbstractDataBackedRestAPIIntegrationTest {

  private DataStructureUtil dataStructureUtil;
  private SpecMilestone specMilestone;

  @BeforeEach
  void setup(final TestSpecInvocationContextProvider.SpecContext specContext) {
    spec = specContext.getSpec();
    specMilestone = specContext.getSpecMilestone();
    startRestAPIAtGenesis(specMilestone);
    dataStructureUtil = specContext.getDataStructureUtil();
  }

  @TestTemplate
  public void shouldReturnBadRequestWhenRequestBodyIsEmpty() throws Exception {
    final Response response = post(PostAttesterSlashingV2.ROUTE, jsonProvider.objectToJSON(""));
    Assertions.assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
  }

  @TestTemplate
  public void shouldReturnBadRequestWhenRequestBodyIsInvalid() throws Exception {
    final Response response =
        post(
            PostAttesterSlashingV2.ROUTE,
            jsonProvider.objectToJSON("{\"foo\": \"bar\"}"),
            Collections.emptyMap(),
            Optional.of(specMilestone.name().toLowerCase(Locale.ROOT)));
    assertThat(response.code()).isEqualTo(400);
  }

  @TestTemplate
  public void shouldReturnServerErrorWhenUnexpectedErrorHappens() throws Exception {
    final tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing slashing =
        dataStructureUtil.randomAttesterSlashing();

    final AttesterSlashing schemaSlashing = new AttesterSlashing(slashing);

    doThrow(new RuntimeException()).when(attesterSlashingPool).addLocal(slashing);

    final Response response =
        post(
            PostAttesterSlashingV2.ROUTE,
            jsonProvider.objectToJSON(schemaSlashing),
            Collections.emptyMap(),
            Optional.of(specMilestone.name().toLowerCase(Locale.ROOT)));
    assertThat(response.code()).isEqualTo(500);
  }

  @TestTemplate
  public void shouldReturnSuccessWhenRequestBodyIsValid() throws Exception {
    final tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing slashing =
        dataStructureUtil.randomAttesterSlashing();

    final AttesterSlashing schemaSlashing = new AttesterSlashing(slashing);

    when(attesterSlashingPool.addLocal(slashing))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));

    final Response response =
        post(
            PostAttesterSlashingV2.ROUTE,
            jsonProvider.objectToJSON(schemaSlashing),
            Collections.emptyMap(),
            Optional.of(specMilestone.name().toLowerCase(Locale.ROOT)));

    verify(attesterSlashingPool).addLocal(slashing);

    assertThat(response.code()).isEqualTo(200);
  }

  @TestTemplate
  void shouldFailWhenMissingConsensusHeader() throws Exception {
    final tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing slashing =
        dataStructureUtil.randomAttesterSlashing();

    final AttesterSlashing schemaSlashing = new AttesterSlashing(slashing);

    final Response response =
        post(PostAttesterSlashingV2.ROUTE, jsonProvider.objectToJSON(schemaSlashing));

    assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);

    final JsonNode resultAsJsonNode = JsonTestUtil.parseAsJsonNode(response.body().string());
    assertThat(resultAsJsonNode.get("message").asText())
        .isEqualTo("(Eth-Consensus-Version) header value was unexpected");
  }

  @TestTemplate
  void shouldFailWhenBadConsensusHeaderValue() throws Exception {

    final tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing slashing =
        dataStructureUtil.randomAttesterSlashing();

    final AttesterSlashing schemaSlashing = new AttesterSlashing(slashing);

    when(attesterSlashingPool.addLocal(slashing))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));

    final Response response =
        post(
            PostAttesterSlashingV2.ROUTE,
            jsonProvider.objectToJSON(schemaSlashing),
            Collections.emptyMap(),
            Optional.of("NonExistingMileStone"));

    assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);

    final JsonNode resultAsJsonNode = JsonTestUtil.parseAsJsonNode(response.body().string());
    assertThat(resultAsJsonNode.get("message").asText())
        .isEqualTo("(Eth-Consensus-Version) header value was unexpected");
  }
}
