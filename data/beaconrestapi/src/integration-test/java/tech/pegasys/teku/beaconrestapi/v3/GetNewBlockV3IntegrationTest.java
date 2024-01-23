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

package tech.pegasys.teku.beaconrestapi.v3;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_BLOCK_VALUE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_EXECUTION_PAYLOAD_BLINDED;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_EXECUTION_PAYLOAD_VALUE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.spec.SpecMilestone.BELLATRIX;
import static tech.pegasys.teku.spec.SpecMilestone.DENEB;

import com.google.common.io.Resources;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import okhttp3.Response;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.api.migrated.BlockRewardData;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v3.validator.GetNewBlockV3;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.http.ContentTypes;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider;
import tech.pegasys.teku.spec.constants.EthConstants;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.BlockContents;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadResult;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@TestSpecContext(allMilestones = true)
public class GetNewBlockV3IntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {

  private DataStructureUtil dataStructureUtil;
  private SpecMilestone specMilestone;
  private final UInt256 executionPayloadValue = UInt256.valueOf(12345);
  private final UInt256 consensusBlockValue = UInt256.valueOf(123);

  private final String consensusBlockValueWei =
      EthConstants.GWEI_TO_WEI.multiply(consensusBlockValue).toDecimalString();

  @BeforeEach
  void setup(final TestSpecInvocationContextProvider.SpecContext specContext) {
    spec = specContext.getSpec();
    specMilestone = specContext.getSpecMilestone();
    startRestAPIAtGenesis(specMilestone);
    dataStructureUtil = specContext.getDataStructureUtil();
    when(executionLayerBlockProductionManager.getCachedPayloadResult(UInt64.ONE))
        .thenReturn(
            Optional.of(
                new ExecutionPayloadResult(
                    mock(ExecutionPayloadContext.class),
                    // we can provide an empty future here as we are only
                    // preparing execution payload value
                    Optional.of(SafeFuture.completedFuture(null)),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(SafeFuture.completedFuture(executionPayloadValue)))));
    final BlockRewardData blockRewardDataMock = mock(BlockRewardData.class);
    when(blockRewardDataMock.getTotal()).thenReturn(consensusBlockValue.toLong());
    when(rewardCalculator.getBlockRewardData(any(), any())).thenReturn(blockRewardDataMock);
  }

  @TestTemplate
  void shouldGetUnBlindedBeaconBlockAsJson() throws IOException {
    assumeThat(specMilestone).isLessThan(DENEB);
    final BeaconBlock beaconBlock = dataStructureUtil.randomBeaconBlock(ONE);
    final BLSSignature signature = beaconBlock.getBlock().getBody().getRandaoReveal();
    when(validatorApiChannel.createUnsignedBlock(eq(UInt64.ONE), eq(signature), any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(beaconBlock)));
    Response response = get(signature, ContentTypes.JSON);
    assertResponseWithHeaders(response, false);
    final String body = response.body().string();
    assertThat(body).isEqualTo(getExpectedBlockAsJson(specMilestone, false, false));
  }

  @TestTemplate
  void shouldGetUnblindedBeaconBlockAsSsz() throws IOException {
    assumeThat(specMilestone).isLessThan(DENEB);
    final BeaconBlock beaconBlock = dataStructureUtil.randomBeaconBlock(ONE);
    final BLSSignature signature = beaconBlock.getBlock().getBody().getRandaoReveal();
    when(validatorApiChannel.createUnsignedBlock(eq(UInt64.ONE), eq(signature), any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(beaconBlock)));
    Response response = get(signature, ContentTypes.OCTET_STREAM);
    assertResponseWithHeaders(response, false);
    final BeaconBlock result =
        spec.getGenesisSchemaDefinitions()
            .getBeaconBlockSchema()
            .sszDeserialize(Bytes.of(response.body().bytes()));
    assertThat(result).isEqualTo(beaconBlock);
  }

  @TestTemplate
  void shouldGetBlindedBeaconBlockAsJson() throws IOException {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(BELLATRIX);
    final BeaconBlock blindedBeaconBlock = dataStructureUtil.randomBlindedBeaconBlock(ONE);
    final BLSSignature signature = blindedBeaconBlock.getBlock().getBody().getRandaoReveal();
    when(validatorApiChannel.createUnsignedBlock(eq(UInt64.ONE), eq(signature), any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(blindedBeaconBlock)));
    Response response = get(signature, ContentTypes.JSON);
    assertResponseWithHeaders(response, true);
    final String body = response.body().string();
    assertThat(body).isEqualTo(getExpectedBlockAsJson(specMilestone, true, false));
  }

  @TestTemplate
  void shouldGetBlindedBeaconBlockAsSsz() throws IOException {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(BELLATRIX);
    final BeaconBlock blindedBeaconBlock = dataStructureUtil.randomBlindedBeaconBlock(ONE);
    final BLSSignature signature = blindedBeaconBlock.getBlock().getBody().getRandaoReveal();
    when(validatorApiChannel.createUnsignedBlock(eq(UInt64.ONE), eq(signature), any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(blindedBeaconBlock)));
    Response response = get(signature, ContentTypes.OCTET_STREAM);
    assertResponseWithHeaders(response, true);
    final BeaconBlock result =
        spec.getGenesisSchemaDefinitions()
            .getBlindedBeaconBlockSchema()
            .sszDeserialize(Bytes.of(response.body().bytes()));
    assertThat(result).isEqualTo(blindedBeaconBlock);
  }

  @TestTemplate
  void shouldGetUnBlindedBlockContentPostDenebAsJson() throws IOException {
    assumeThat(specMilestone).isEqualTo(DENEB);
    final BlockContents blockContents = dataStructureUtil.randomBlockContents(ONE);
    final BLSSignature signature = blockContents.getBlock().getBody().getRandaoReveal();
    when(validatorApiChannel.createUnsignedBlock(eq(UInt64.ONE), eq(signature), any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(blockContents)));
    Response response = get(signature, ContentTypes.JSON);
    assertResponseWithHeaders(response, false);
    final String body = response.body().string();
    assertThat(body).isEqualTo(getExpectedBlockAsJson(specMilestone, false, true));
  }

  @TestTemplate
  void shouldGetUnBlindedBlockContentPostDenebAsSsz() throws IOException {
    assumeThat(specMilestone).isEqualTo(DENEB);
    final BlockContents blockContents = dataStructureUtil.randomBlockContents(ONE);
    final BLSSignature signature = blockContents.getBlock().getBody().getRandaoReveal();
    when(validatorApiChannel.createUnsignedBlock(eq(UInt64.ONE), eq(signature), any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(blockContents)));
    Response response = get(signature, ContentTypes.OCTET_STREAM);
    assertResponseWithHeaders(response, false);
    final BlockContents result =
        (BlockContents)
            spec.getGenesisSchemaDefinitions()
                .getBlockContainerSchema()
                .sszDeserialize(Bytes.of(response.body().bytes()));
    assertThat(result).isEqualTo(blockContents);
  }

  @TestTemplate
  void shouldFailWhenNoBlockProduced() throws IOException {
    final BeaconBlock beaconBlock = dataStructureUtil.randomBeaconBlock(ONE);
    final BLSSignature signature = beaconBlock.getBlock().getBody().getRandaoReveal();
    when(validatorApiChannel.createUnsignedBlock(eq(UInt64.ONE), eq(signature), any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    Response response = get(signature, ContentTypes.JSON);
    assertThat(response.code()).isEqualTo(SC_INTERNAL_SERVER_ERROR);
    final String body = response.body().string();
    assertThat(body).contains("Unable to produce a block");
  }

  private Response get(final BLSSignature signature, final String contentType) throws IOException {
    return getResponse(
        GetNewBlockV3.ROUTE.replace("{slot}", "1"),
        Map.of("randao_reveal", signature.toString()),
        contentType);
  }

  private String getExpectedBlockAsJson(
      final SpecMilestone specMilestone, final boolean blinded, final boolean blockContents)
      throws IOException {
    final String fileName =
        String.format(
            "new%s%s%s.json",
            blinded ? "Blinded" : "",
            blockContents ? "BlockContents" : "Block",
            specMilestone.name());
    return Resources.toString(
        Resources.getResource(GetNewBlockV3IntegrationTest.class, fileName), UTF_8);
  }

  private void assertResponseWithHeaders(Response response, boolean blinded) {
    assertThat(response.code()).isEqualTo(SC_OK);
    assertThat(response.header(HEADER_CONSENSUS_VERSION))
        .isEqualTo(specMilestone.name().toLowerCase(Locale.ROOT));
    assertThat(response.header(HEADER_EXECUTION_PAYLOAD_BLINDED))
        .isEqualTo(Boolean.toString(blinded));
    assertThat(response.header(HEADER_EXECUTION_PAYLOAD_VALUE))
        .isEqualTo(executionPayloadValue.toDecimalString());
    assertThat(response.header(HEADER_CONSENSUS_BLOCK_VALUE)).isEqualTo(consensusBlockValueWei);
  }
}
