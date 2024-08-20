/*
 * Copyright Consensys Software Inc., 2022
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
import static tech.pegasys.teku.infrastructure.http.ContentTypes.OCTET_STREAM;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONTENT_ENCODING;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPInputStream;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.schema.Version;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v2.beacon.GetBlock;
import tech.pegasys.teku.infrastructure.http.ContentTypes;
import tech.pegasys.teku.infrastructure.json.JsonTestUtil;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.BeaconBlockBodyAltair;

public class GetBlockV2IntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {

  @Test
  public void shouldGetBlock() throws Exception {
    startRestAPIAtGenesis(SpecMilestone.PHASE0);
    final List<SignedBlockAndState> created = createBlocksAtSlots(10);
    final Response response = get("head");
    final JsonNode responseAsJsonNode = JsonTestUtil.parseAsJsonNode(response.body().string());
    assertThat(responseAsJsonNode.get("version").asText()).isEqualTo(Version.phase0.name());
    final SignedBeaconBlock signedBlock =
        JsonUtil.parse(
            responseAsJsonNode.get("data").toString(),
            spec.getGenesisSchemaDefinitions()
                .getSignedBeaconBlockSchema()
                .getJsonTypeDefinition());
    assertThat(signedBlock.getBeaconBlock().get().getBody()).isInstanceOf(BeaconBlockBody.class);
    final SignedBlockAndState block = created.get(0);
    assertThat(signedBlock).isEqualTo(block.getBlock());
    assertThat(response.header(HEADER_CONSENSUS_VERSION)).isEqualTo(Version.phase0.name());
  }

  @Test
  public void shouldGetAltairBlock() throws Exception {
    startRestAPIAtGenesis(SpecMilestone.ALTAIR);
    final List<SignedBlockAndState> created = createBlocksAtSlots(10);
    final Response response = get("head");
    final JsonNode responseAsJsonNode = JsonTestUtil.parseAsJsonNode(response.body().string());
    assertThat(responseAsJsonNode.get("version").asText()).isEqualTo(Version.altair.name());
    final SignedBeaconBlock signedBlock =
        JsonUtil.parse(
            responseAsJsonNode.get("data").toString(),
            spec.getGenesisSchemaDefinitions()
                .toVersionAltair()
                .get()
                .getSignedBeaconBlockSchema()
                .getJsonTypeDefinition());
    assertThat(signedBlock.getBeaconBlock().get().getBody())
        .isInstanceOf(BeaconBlockBodyAltair.class);

    assertThat(signedBlock.getSignature().toString())
        .isEqualTo(created.get(0).getBlock().getSignature().toString());
    assertThat(signedBlock.getRoot().toHexString())
        .isEqualTo(created.get(0).getBlock().getMessage().getRoot().toHexString());
    assertThat(
            signedBlock
                .getBeaconBlock()
                .get()
                .getBody()
                .getOptionalSyncAggregate()
                .get()
                .getSyncCommitteeBits()
                .getBitCount())
        .isZero();
    assertThat(response.header(HEADER_CONSENSUS_VERSION)).isEqualTo(Version.altair.name());
  }

  @Test
  public void shouldGetAltairBlockAsSsz() throws IOException {
    startRestAPIAtGenesis(SpecMilestone.ALTAIR);
    createBlocksAtSlots(10);
    final Response response = get("head", OCTET_STREAM);
    assertThat(response.code()).isEqualTo(SC_OK);
    assertThat(response.header(HEADER_CONSENSUS_VERSION)).isEqualTo(Version.altair.name());
  }

  @Test
  public void shouldGetAltairBlockAsGzip() throws IOException {
    startRestAPIAtGenesis(SpecMilestone.ALTAIR);
    createBlocksAtSlots(10);
    final Response response = getGzip("head", ContentTypes.JSON);
    assertThat(response.code()).isEqualTo(SC_OK);
    assertThat(response.header(HEADER_CONSENSUS_VERSION)).isEqualTo(Version.altair.name());
    assertThat(response.header(HEADER_CONTENT_ENCODING)).isEqualTo("gzip");

    // Decompress response
    final byte[] responseBody = response.body().bytes();
    GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(responseBody));
    byte[] bytesResult = gis.readAllBytes();
    String block = new String(bytesResult, StandardCharsets.UTF_8);

    // Check block signatures are equivalent to ensure same block
    JsonNode node = jsonProvider.getObjectMapper().readTree(block);
    String blockSignature = node.get("data").get("signature").asText();
    String expectedSignature =
        chainBuilder.getLatestBlockAndState().getBlock().getSignature().toString();
    assertThat(blockSignature).isEqualTo(expectedSignature);
  }

  public Response get(final String blockIdString, final String contentType) throws IOException {
    return getResponse(GetBlock.ROUTE.replace("{block_id}", blockIdString), contentType);
  }

  public Response getGzip(final String blockIdString, final String contentType) throws IOException {
    return getResponse(GetBlock.ROUTE.replace("{block_id}", blockIdString), contentType, "gzip");
  }

  public Response get(final String blockIdString) throws IOException {
    return getResponse(GetBlock.ROUTE.replace("{block_id}", blockIdString));
  }
}
