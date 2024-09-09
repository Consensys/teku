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

package tech.pegasys.teku.beaconrestapi.handlers.v3.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_BLOCK_VALUE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_EXECUTION_PAYLOAD_BLINDED;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_EXECUTION_PAYLOAD_VALUE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RANDAO_REVEAL;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SLOT;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataEmptyResponse;
import static tech.pegasys.teku.infrastructure.restapi.MetadataTestUtil.verifyMetadataErrorResponse;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.spec.SpecMilestone.BELLATRIX;
import static tech.pegasys.teku.spec.SpecMilestone.DENEB;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.api.schema.Version;
import tech.pegasys.teku.beaconrestapi.AbstractMigratedBeaconHandlerTest;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.http.HttpStatusCodes;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.BlockContents;
import tech.pegasys.teku.spec.datastructures.metadata.BlockContainerAndMetaData;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@TestSpecContext(allMilestones = true)
public class GetNewBlockV3Test extends AbstractMigratedBeaconHandlerTest {

  private SpecMilestone specMilestone;
  protected final BLSSignature signature = BLSTestUtil.randomSignature(1234);

  @BeforeEach
  public void setup(final TestSpecInvocationContextProvider.SpecContext specContext) {
    spec = specContext.getSpec();
    dataStructureUtil = new DataStructureUtil(spec);
    specMilestone = specContext.getSpecMilestone();
    setHandler(new GetNewBlockV3(validatorDataProvider, schemaDefinitionCache));
    request.setPathParameter(SLOT, "1");
    request.setQueryParameter(RANDAO_REVEAL, signature.toBytesCompressed().toHexString());
    when(validatorDataProvider.getMilestoneAtSlot(UInt64.ONE)).thenReturn(SpecMilestone.ALTAIR);
  }

  @TestTemplate
  void shouldHandleBlindedBeaconBlocks() throws Exception {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(BELLATRIX);
    final BlockContainerAndMetaData blockContainerAndMetaData =
        dataStructureUtil.randomBlindedBlockContainerAndMetaData(ONE);

    doReturn(SafeFuture.completedFuture(Optional.of(blockContainerAndMetaData)))
        .when(validatorDataProvider)
        .produceBlock(ONE, signature, Optional.empty(), Optional.empty());

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(HttpStatusCodes.SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(blockContainerAndMetaData);
    assertThat(request.getResponseHeaders(HEADER_CONSENSUS_VERSION))
        .isEqualTo(Version.fromMilestone(blockContainerAndMetaData.specMilestone()).name());
    assertThat(request.getResponseHeaders(HEADER_EXECUTION_PAYLOAD_BLINDED))
        .isEqualTo(Boolean.toString(true));
    assertThat(request.getResponseHeaders(HEADER_EXECUTION_PAYLOAD_VALUE))
        .isEqualTo(blockContainerAndMetaData.executionPayloadValue().toDecimalString());
    assertThat(request.getResponseHeaders(HEADER_CONSENSUS_BLOCK_VALUE))
        .isEqualTo(blockContainerAndMetaData.consensusBlockValue().toDecimalString());
  }

  @TestTemplate
  void shouldHandleUnBlindedBeaconBlocks() throws Exception {
    assumeThat(specMilestone).isLessThan(DENEB);
    BlockContainerAndMetaData blockContainerAndMetaData =
        dataStructureUtil.randomBlockContainerAndMetaData(ONE);
    doReturn(SafeFuture.completedFuture(Optional.of(blockContainerAndMetaData)))
        .when(validatorDataProvider)
        .produceBlock(ONE, signature, Optional.empty(), Optional.empty());

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(HttpStatusCodes.SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(blockContainerAndMetaData);
    assertThat(request.getResponseHeaders(HEADER_CONSENSUS_VERSION))
        .isEqualTo(Version.fromMilestone(blockContainerAndMetaData.specMilestone()).name());
    assertThat(request.getResponseHeaders(HEADER_EXECUTION_PAYLOAD_BLINDED))
        .isEqualTo(Boolean.toString(false));
    assertThat(request.getResponseHeaders(HEADER_EXECUTION_PAYLOAD_VALUE))
        .isEqualTo(blockContainerAndMetaData.executionPayloadValue().toDecimalString());
    assertThat(request.getResponseHeaders(HEADER_CONSENSUS_BLOCK_VALUE))
        .isEqualTo(blockContainerAndMetaData.consensusBlockValue().toDecimalString());
  }

  @TestTemplate
  void shouldHandleUnBlindedBlockContentsPostDeneb() throws Exception {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(DENEB);
    final BlockContents blockContents = dataStructureUtil.randomBlockContents(ONE);
    final BlockContainerAndMetaData blockContainerAndMetaData =
        dataStructureUtil.randomBlockContainerAndMetaData(blockContents, ONE);
    doReturn(SafeFuture.completedFuture(Optional.of(blockContainerAndMetaData)))
        .when(validatorDataProvider)
        .produceBlock(ONE, signature, Optional.empty(), Optional.empty());

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(HttpStatusCodes.SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(blockContainerAndMetaData);
    assertThat(request.getResponseHeaders(HEADER_CONSENSUS_VERSION))
        .isEqualTo(Version.fromMilestone(blockContainerAndMetaData.specMilestone()).name());
    assertThat(request.getResponseHeaders(HEADER_EXECUTION_PAYLOAD_BLINDED)).isEqualTo("false");
    assertThat(request.getResponseHeaders(HEADER_EXECUTION_PAYLOAD_VALUE))
        .isEqualTo(blockContainerAndMetaData.executionPayloadValue().toDecimalString());
    assertThat(request.getResponseHeaders(HEADER_CONSENSUS_BLOCK_VALUE))
        .isEqualTo(blockContainerAndMetaData.consensusBlockValue().toDecimalString());
  }

  @TestTemplate
  void shouldThrowExceptionWhenEmptyBlock() throws Exception {
    doReturn(SafeFuture.completedFuture(Optional.empty()))
        .when(validatorDataProvider)
        .produceBlock(ONE, signature, Optional.empty(), Optional.empty());

    handler.handleRequest(request);
    assertThat(request.getResponseCode()).isEqualTo(SC_INTERNAL_SERVER_ERROR);
    assertThat(request.getResponseBody().toString()).contains("Unable to produce a block");
  }

  @TestTemplate
  void metadata_shouldHandle204() {
    verifyMetadataEmptyResponse(handler, SC_NO_CONTENT);
  }

  @TestTemplate
  void metadata_shouldHandle503() throws JsonProcessingException {
    verifyMetadataErrorResponse(handler, SC_SERVICE_UNAVAILABLE);
  }
}
