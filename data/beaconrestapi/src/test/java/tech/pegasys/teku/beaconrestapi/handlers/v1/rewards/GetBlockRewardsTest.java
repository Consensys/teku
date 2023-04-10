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
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_IMPLEMENTED;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.io.Resources;
import java.io.IOException;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.migrated.BlockRewardData;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.infrastructure.http.HttpStatusCodes;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;

public class GetBlockRewardsTest extends AbstractMigratedBeaconHandlerTest {
  private final BlockRewardData data =
      new BlockRewardData(
          UInt64.valueOf(123),
          UInt64.valueOf(123),
          123L,
          UInt64.valueOf(123),
          UInt64.valueOf(123),
          UInt64.valueOf(123));
  private final ObjectAndMetaData<BlockRewardData> blockRewardsResult =
      new ObjectAndMetaData<>(data, SpecMilestone.ALTAIR, false, true, true);

  @BeforeEach
  void setUp() {
    setHandler(new GetBlockRewards());
  }

  @Test
  void metadata_shouldHandle400() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, HttpStatusCodes.SC_BAD_REQUEST);
  }

  @Test
  void metadata_shouldHandle404() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, HttpStatusCodes.SC_NOT_FOUND);
  }

  @Test
  void metadata_shouldHandle500() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_INTERNAL_SERVER_ERROR);
  }

  @Test
  void metadata_shouldHandle501() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_NOT_IMPLEMENTED);
  }

  @Test
  void metadata_shouldHandle200() throws IOException {
    final String data = getResponseStringFromMetadata(handler, SC_OK, blockRewardsResult);
    final String expected =
        Resources.toString(
            Resources.getResource(GetBlockRewardsTest.class, "blockRewardsData.json"), UTF_8);
    AssertionsForClassTypes.assertThat(data).isEqualTo(expected);
  }
}
