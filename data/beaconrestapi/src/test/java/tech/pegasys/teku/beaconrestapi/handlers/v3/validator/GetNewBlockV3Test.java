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
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_EXECUTION_PAYLOAD_BLINDED;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_EXECUTION_PAYLOAD_VALUE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RANDAO_REVEAL;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SLOT;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.spec.SpecMilestone.BELLATRIX;
import static tech.pegasys.teku.spec.SpecMilestone.CAPELLA;
import static tech.pegasys.teku.spec.SpecMilestone.DENEB;

import java.util.Optional;
import org.apache.tuweni.units.bigints.UInt256;
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
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.BlindedBlockContents;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.BlockContents;
import tech.pegasys.teku.spec.datastructures.metadata.BlockContainerAndMetaData;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.ChainDataUnavailableException;

@TestSpecContext(allMilestones = true)
public class GetNewBlockV3Test extends AbstractMigratedBeaconHandlerTest {

  private SpecMilestone specMilestone;
  private final UInt256 blockValue = UInt256.valueOf(12345);
  protected final BLSSignature signature = BLSTestUtil.randomSignature(1234);

  @BeforeEach
  public void setup(TestSpecInvocationContextProvider.SpecContext specContext) {
    spec = specContext.getSpec();
    specMilestone = specContext.getSpecMilestone();
    setHandler(new GetNewBlockV3(validatorDataProvider, schemaDefinitionCache));
    request.setPathParameter(SLOT, "1");
    request.setQueryParameter(RANDAO_REVEAL, signature.toBytesCompressed().toHexString());
    when(validatorDataProvider.getMilestoneAtSlot(UInt64.ONE)).thenReturn(SpecMilestone.ALTAIR);
  }

  @TestTemplate
  void shouldHandleBlindedBeaconBlocks() throws Exception {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(BELLATRIX).isLessThanOrEqualTo(CAPELLA);
    dataStructureUtil = new DataStructureUtil(spec);
    final BeaconBlock blindedBeaconBlock = dataStructureUtil.randomBlindedBeaconBlock(ONE);
    BlockContainerAndMetaData blockContainerAndMetaData =
        new BlockContainerAndMetaData(
            blindedBeaconBlock,
            spec.getGenesisSpec().getMilestone(),
            false,
            false,
            false,
            blockValue);
    doReturn(SafeFuture.completedFuture(Optional.of(blockContainerAndMetaData)))
        .when(validatorDataProvider)
        .produceBlock(ONE, signature, Optional.empty());

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(HttpStatusCodes.SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(blockContainerAndMetaData);
    assertThat(request.getHeader(HEADER_CONSENSUS_VERSION))
        .isEqualTo(Version.fromMilestone(blockContainerAndMetaData.getMilestone()).name());
    assertThat(request.getHeader(HEADER_EXECUTION_PAYLOAD_BLINDED))
        .isEqualTo(Boolean.toString(true));
    assertThat(request.getHeader(HEADER_EXECUTION_PAYLOAD_VALUE))
        .isEqualTo(blockValue.toDecimalString());
  }

  @TestTemplate
  void shouldHandleUnBlindedBeaconBlocks() throws Exception {
    assumeThat(specMilestone).isLessThan(DENEB);
    dataStructureUtil = new DataStructureUtil(spec);
    final BeaconBlock beaconblock = dataStructureUtil.randomBeaconBlock(ONE);
    BlockContainerAndMetaData blockContainerAndMetaData =
        new BlockContainerAndMetaData(
            beaconblock, spec.getGenesisSpec().getMilestone(), false, false, false, blockValue);
    doReturn(SafeFuture.completedFuture(Optional.of(blockContainerAndMetaData)))
        .when(validatorDataProvider)
        .produceBlock(ONE, signature, Optional.empty());

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(HttpStatusCodes.SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(blockContainerAndMetaData);
    assertThat(request.getHeader(HEADER_CONSENSUS_VERSION))
        .isEqualTo(Version.fromMilestone(blockContainerAndMetaData.getMilestone()).name());
    assertThat(request.getHeader(HEADER_EXECUTION_PAYLOAD_BLINDED))
        .isEqualTo(Boolean.toString(false));
    assertThat(request.getHeader(HEADER_EXECUTION_PAYLOAD_VALUE))
        .isEqualTo(blockValue.toDecimalString());
  }

  @TestTemplate
  void shouldHandleUnBlindedBlockContentsPostDeneb() throws Exception {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(DENEB);
    dataStructureUtil = new DataStructureUtil(spec);
    final BlockContents blockContents = dataStructureUtil.randomBlockContents(ONE);
    BlockContainerAndMetaData blockContainerAndMetaData =
        new BlockContainerAndMetaData(
            blockContents, spec.getGenesisSpec().getMilestone(), false, false, false, blockValue);
    doReturn(SafeFuture.completedFuture(Optional.of(blockContainerAndMetaData)))
        .when(validatorDataProvider)
        .produceBlock(ONE, signature, Optional.empty());

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(HttpStatusCodes.SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(blockContainerAndMetaData);
    assertThat(request.getHeader(HEADER_CONSENSUS_VERSION))
        .isEqualTo(Version.fromMilestone(blockContainerAndMetaData.getMilestone()).name());
    assertThat(request.getHeader(HEADER_EXECUTION_PAYLOAD_BLINDED)).isEqualTo("false");
    assertThat(request.getHeader(HEADER_EXECUTION_PAYLOAD_VALUE))
        .isEqualTo(blockValue.toDecimalString());
  }

  @TestTemplate
  void shouldHandleBlindedBlockContentsPostDeneb() throws Exception {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(DENEB);
    dataStructureUtil = new DataStructureUtil(spec);
    final BlindedBlockContents blindedBlockContents =
        dataStructureUtil.randomBlindedBlockContents(ONE);
    BlockContainerAndMetaData blockContainerAndMetaData =
        new BlockContainerAndMetaData(
            blindedBlockContents,
            spec.getGenesisSpec().getMilestone(),
            false,
            false,
            false,
            blockValue);
    doReturn(SafeFuture.completedFuture(Optional.of(blockContainerAndMetaData)))
        .when(validatorDataProvider)
        .produceBlock(ONE, signature, Optional.empty());

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(HttpStatusCodes.SC_OK);
    assertThat(request.getResponseBody()).isEqualTo(blockContainerAndMetaData);
    assertThat(request.getHeader(HEADER_CONSENSUS_VERSION))
        .isEqualTo(Version.fromMilestone(blockContainerAndMetaData.getMilestone()).name());
    assertThat(request.getHeader(HEADER_EXECUTION_PAYLOAD_BLINDED)).isEqualTo("true");
    assertThat(request.getHeader(HEADER_EXECUTION_PAYLOAD_VALUE))
        .isEqualTo(blockValue.toDecimalString());
  }

  @TestTemplate
  void shouldThrowExceptionWithEmptyBlock() throws Exception {

    doReturn(SafeFuture.completedFuture(Optional.empty()))
        .when(validatorDataProvider)
        .produceBlock(ONE, signature, Optional.empty());

    handler.handleRequest(request);
    assertThat(request.getResponseError()).containsInstanceOf(ChainDataUnavailableException.class);
  }
}
