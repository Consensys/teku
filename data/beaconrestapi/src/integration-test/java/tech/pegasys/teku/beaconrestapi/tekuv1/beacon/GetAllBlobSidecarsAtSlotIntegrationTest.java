/*
 * Copyright Consensys Software Inc., 2023
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

package tech.pegasys.teku.beaconrestapi.tekuv1.beacon;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.beaconrestapi.BeaconRestApiTypes.BLOB_INDICES_PARAMETER;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition.listOf;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.tekuv1.beacon.GetAllBlobSidecarsAtSlot;
import tech.pegasys.teku.ethereum.json.types.SharedApiTypes;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;

public class GetAllBlobSidecarsAtSlotIntegrationTest
    extends AbstractDataBackedRestAPIIntegrationTest {

  @BeforeEach
  public void beforeEach() {
    startRestApiAtGenesisStoringNonCanonicalBlocks(SpecMilestone.DENEB);
  }

  @Test
  public void shouldGetNonCanonicalBlobSidecars() throws IOException {
    chainUpdater.blockOptions.setGenerateRandomBlobs(true);
    chainUpdater.blockOptions.setGenerateRandomBlobsCount(Optional.of(4));

    final UInt64 targetSlot = UInt64.valueOf(3);
    chainUpdater.advanceChainUntil(targetSlot);

    final ChainBuilder fork = chainBuilder.fork();
    SignedBlockAndState nonCanonicalBlock = fork.generateNextBlock(chainUpdater.blockOptions);

    final List<BlobSidecar> nonCanonicalBlobSidecars =
        fork.getBlobSidecars(nonCanonicalBlock.getRoot());
    chainUpdater.saveBlock(nonCanonicalBlock, nonCanonicalBlobSidecars);

    SignedBlockAndState canonical = chainBuilder.generateNextBlock(1);
    chainUpdater.updateBestBlock(canonical);
    chainUpdater.finalizeEpoch(targetSlot.plus(1));

    final Response response =
        get(
            nonCanonicalBlock.getSlot(),
            List.of(UInt64.ZERO, UInt64.ONE, UInt64.valueOf(2), UInt64.valueOf(3)));

    assertThat(response.code()).isEqualTo(SC_OK);

    final List<BlobSidecar> result = parseBlobSidecars(response);
    assertThat(result).isEqualTo(nonCanonicalBlobSidecars);
  }

  public Response get(final UInt64 slot, final List<UInt64> indices) throws IOException {
    return getResponse(
        GetAllBlobSidecarsAtSlot.ROUTE.replace("{slot}", slot.toString()),
        Map.of(
            BLOB_INDICES_PARAMETER.getName(),
            indices.stream().map(UInt64::toString).collect(Collectors.joining(","))));
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
