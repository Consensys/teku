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

package tech.pegasys.teku.beaconrestapi.v1.beacon;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition.listOf;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.GetBlobSidecars;
import tech.pegasys.teku.ethereum.json.types.SharedApiTypes;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;

public class GetBlobSidecarsIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {

  @BeforeEach
  public void beforeEach() {
    startRestAPIAtGenesis(SpecMilestone.DENEB);
  }

  @Test
  public void shouldGetBlobSidecars() throws Exception {
    // generate 4 blobs per block
    chainUpdater.blockOptions.setGenerateRandomBlobs(true);
    chainUpdater.blockOptions.setGenerateRandomBlobsCount(Optional.of(4));

    // up to slot 3
    final UInt64 targetSlot = UInt64.valueOf(3);
    final SignedBlockAndState lastBlock = chainUpdater.advanceChainUntil(targetSlot);
    chainUpdater.updateBestBlock(lastBlock);
    final List<BlobSidecar> expected =
        recentChainData.retrieveBlobSidecars(lastBlock.getSlotAndBlockRoot()).get();

    Response response = get("head");

    assertThat(response.code()).isEqualTo(SC_OK);

    final List<BlobSidecar> actual = parseBlobSidecars(response);
    assertThat(actual).hasSize(expected.size());
    assertThat(actual).isEqualTo(expected);
  }

  public Response get(final String blockIdString, final String contentType) throws IOException {
    return getResponse(GetBlobSidecars.ROUTE.replace("{block_id}", blockIdString), contentType);
  }

  public Response get(final String blockIdString) throws IOException {
    return getResponse(GetBlobSidecars.ROUTE.replace("{block_id}", blockIdString));
  }

  private List<BlobSidecar> parseBlobSidecars(final Response response) throws IOException {
    final DeserializableTypeDefinition<BlobSidecar> blobSidecarTypeDefinition =
        SchemaDefinitionsDeneb.required(spec.getGenesisSchemaDefinitions())
            .getBlobSidecarSchema()
            .getJsonTypeDefinition();
    final DeserializableTypeDefinition<List<BlobSidecar>> jsonTypeDefinition =
        SharedApiTypes.withDataWrapper("blobSidecars", listOf(blobSidecarTypeDefinition));

    final List<BlobSidecar> result = JsonUtil.parse(response.body().string(), jsonTypeDefinition);
    return result;
  }
}
