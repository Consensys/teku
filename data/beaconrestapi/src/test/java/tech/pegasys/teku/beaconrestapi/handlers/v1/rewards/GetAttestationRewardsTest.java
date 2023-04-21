/*
 * Copyright ConsenSys Software Inc., 2023
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

package tech.pegasys.teku.beaconrestapi.handlers.v1.rewards;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.*;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.io.Resources;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.migrated.AttestationRewardsData;
import tech.pegasys.teku.api.migrated.GetAttestationRewardsResponse;
import tech.pegasys.teku.api.migrated.IdealAttestationReward;
import tech.pegasys.teku.api.migrated.TotalAttestationReward;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;

class GetAttestationRewardsTest extends AbstractMigratedBeaconHandlerTest {

  // Values from getAttestationRewards.json
  private final GetAttestationRewardsResponse responseData;

  {
    IdealAttestationReward idealAttestationReward =
        new IdealAttestationReward(1000000000L, 2500L, 5000L, 5000L);
    TotalAttestationReward totalAttestationReward =
        new TotalAttestationReward(0L, 2000L, 2000L, 4000L, 2000L);
    AttestationRewardsData attestationRewardsData =
        new AttestationRewardsData(
            List.of(idealAttestationReward), List.of(totalAttestationReward));
    responseData = new GetAttestationRewardsResponse(false, false, attestationRewardsData);
  }

  @BeforeEach
  void setup() {
    setHandler(new GetAttestationRewards());
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
    final String data = getResponseStringFromMetadata(handler, SC_OK, responseData);
    final String expected =
        Resources.toString(
            Resources.getResource(GetAttestationRewards.class, "getAttestationRewards.json"),
            UTF_8);
    assertThat(data).isEqualTo(String.format(expected, responseData));
  }
}
