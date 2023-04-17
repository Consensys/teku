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
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.getResponseStringFromMetadata;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.io.Resources;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.migrated.BlockRewardData;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerWithChainDataProviderTest;
import tech.pegasys.teku.infrastructure.http.HttpStatusCodes;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class GetBlockRewardsTest extends AbstractMigratedBeaconHandlerWithChainDataProviderTest {
  private final BlockRewardData data =
      new BlockRewardData(
          123, 123L, 123L, UInt64.valueOf(123), UInt64.valueOf(123), UInt64.valueOf(123));
  private final ObjectAndMetaData<BlockRewardData> blockRewardsResult =
      new ObjectAndMetaData<>(data, SpecMilestone.ALTAIR, false, true, true);

  @BeforeEach
  void setUp() {
    setHandler(new GetBlockRewards(chainDataProvider));
    request.setPathParameter("block_id", "head");
  }

  @Test
  void shouldReturnBlockAndRewardDataInformation() // TODO want to advance chain to have rewards
      throws JsonProcessingException, ExecutionException, InterruptedException {
    spec = TestSpecFactory.createMinimalAltair();
    dataStructureUtil = new DataStructureUtil(spec);
    initialise(SpecMilestone.ALTAIR);
    genesis();

    chainUpdater.updateBestBlock(chainUpdater.advanceChain(20));

    final ChainBuilder.BlockOptions blockOptions =
        ChainBuilder.BlockOptions.create()
            .addAttestation(dataStructureUtil.randomAttestation(UInt64.valueOf(21)))
            .addAttesterSlashing(
                dataStructureUtil.randomAttesterSlashingAtSlot(UInt64.valueOf(21)));
    SignedBlockAndState latestBlockAndState = chainBuilder.generateBlockAtSlot(21, blockOptions);

    chainUpdater.saveBlock(latestBlockAndState);
    chainUpdater.updateBestBlock(latestBlockAndState);
    //    chainUpdater.finalizeCurrentChain();

    handler.handleRequest(request);

    final ObjectAndMetaData<BlockRewardData> output =
        chainDataProvider.getBlockRewardsFromBlockId("head").get().orElseThrow();

    System.out.println(output.getData()); // TODO remove (debugging)
    Assertions.assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(output);
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
  void metadata_shouldHandle200() throws IOException {
    final String data = getResponseStringFromMetadata(handler, SC_OK, blockRewardsResult);
    final String expected =
        Resources.toString(
            Resources.getResource(GetBlockRewardsTest.class, "blockRewardsData.json"), UTF_8);
    AssertionsForClassTypes.assertThat(data).isEqualTo(expected);
  }
}
