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

package tech.pegasys.teku.beaconrestapi.handlers.v1.beacon;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.io.Resources;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.migrated.StateValidatorBalanceData;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerWithChainDataProviderTest;
import tech.pegasys.teku.infrastructure.restapi.StubRestApiRequest;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;

public class GetStateValidatorBalancesTest
    extends AbstractMigratedBeaconHandlerWithChainDataProviderTest {

  @BeforeEach
  void setup() {
    initialise(SpecMilestone.ALTAIR);
    genesis();

    setHandler(new GetStateValidatorBalances(chainDataProvider));
  }

  @Test
  public void shouldGetSpecifiedValidatorBalancesFromState() throws Exception {
    StubRestApiRequest request =
        StubRestApiRequest.builder()
            .metadata(handler.getMetadata())
            .pathParameter("state_id", "head")
            .listQueryParameter("id", List.of("1", "2"))
            .build();

    final Optional<ObjectAndMetaData<List<StateValidatorBalanceData>>> stateValidatorBalancesData =
        chainDataProvider.getStateValidatorBalances("head", List.of("1", "2")).get();

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(stateValidatorBalancesData.orElseThrow());
  }

  @Test
  public void shouldGetAllValidatorBalancesFromState() throws Exception {
    StubRestApiRequest request =
        StubRestApiRequest.builder()
            .metadata(handler.getMetadata())
            .pathParameter("state_id", "head")
            .build();

    final Optional<ObjectAndMetaData<List<StateValidatorBalanceData>>> stateValidatorBalancesData =
        chainDataProvider.getStateValidatorBalances("head", List.of()).get();

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(stateValidatorBalancesData.orElseThrow());
  }

  @Test
  void metadata_shouldHandle400() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_BAD_REQUEST);
  }

  @Test
  void metadata_shouldHandle404() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_NOT_FOUND);
  }

  @Test
  void metadata_shouldHandle500() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_INTERNAL_SERVER_ERROR);
  }

  @Test
  void metadata_shouldHandle200() throws IOException {
    List<StateValidatorBalanceData> stateValidatorBalanceData = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      stateValidatorBalanceData.add(
          new StateValidatorBalanceData(UInt64.valueOf(i), dataStructureUtil.randomUInt64()));
    }

    ObjectAndMetaData<List<StateValidatorBalanceData>> responseData =
        withMetaData(stateValidatorBalanceData);

    final String data = getResponseStringFromMetadata(handler, SC_OK, responseData);
    final String expected =
        Resources.toString(
            Resources.getResource(
                GetStateValidatorBalancesTest.class, "getStateValidatorBalances.json"),
            UTF_8);
    assertThat(data).isEqualTo(expected);
  }
}
