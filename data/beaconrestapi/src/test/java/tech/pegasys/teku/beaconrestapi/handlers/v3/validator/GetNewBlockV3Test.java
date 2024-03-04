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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_BLOCK_VALUE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_EXECUTION_PAYLOAD_BLINDED;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_EXECUTION_PAYLOAD_VALUE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.RANDAO_REVEAL;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.SLOT;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.spec.SpecMilestone.ALTAIR;
import static tech.pegasys.teku.spec.SpecMilestone.BELLATRIX;
import static tech.pegasys.teku.spec.SpecMilestone.DENEB;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Optional;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.infrastructure.logging.LogCaptor;
import tech.pegasys.teku.api.RewardCalculator;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.api.migrated.BlockRewardData;
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
import tech.pegasys.teku.spec.constants.EthConstants;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.BlockContents;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadResult;
import tech.pegasys.teku.spec.datastructures.metadata.BlockContainerAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerBlockProductionManager;
import tech.pegasys.teku.spec.logic.common.util.BlockRewardCalculatorUtil;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;

@TestSpecContext(allMilestones = true)
public class GetNewBlockV3Test extends AbstractMigratedBeaconHandlerTest {

  private SpecMilestone specMilestone;
  private final UInt256 executionPayloadValue = UInt256.valueOf(12345);
  private final UInt256 consensusBlockValue = UInt256.valueOf(6789);
  private final UInt256 consensusBlockValueWei =
      EthConstants.GWEI_TO_WEI.multiply(consensusBlockValue);
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
    final BeaconBlock blindedBeaconBlock = dataStructureUtil.randomBlindedBeaconBlock(ONE);
    final BlockContainerAndMetaData<BlockContainer> blockContainerAndMetaData =
        new BlockContainerAndMetaData<>(
            blindedBeaconBlock,
            spec.getGenesisSpec().getMilestone(),
            executionPayloadValue,
            consensusBlockValue);
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
        .isEqualTo(executionPayloadValue.toDecimalString());
    assertThat(request.getResponseHeaders(HEADER_CONSENSUS_BLOCK_VALUE))
        .isEqualTo(consensusBlockValue.toDecimalString());
  }

  @TestTemplate
  void shouldHandleUnBlindedBeaconBlocks() throws Exception {
    assumeThat(specMilestone).isLessThan(DENEB);
    final BeaconBlock beaconblock = dataStructureUtil.randomBeaconBlock(ONE);
    final BlockContainerAndMetaData<BlockContainer> blockContainerAndMetaData =
        new BlockContainerAndMetaData<>(
            beaconblock,
            spec.getGenesisSpec().getMilestone(),
            executionPayloadValue,
            consensusBlockValue);
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
        .isEqualTo(executionPayloadValue.toDecimalString());
    assertThat(request.getResponseHeaders(HEADER_CONSENSUS_BLOCK_VALUE))
        .isEqualTo(consensusBlockValue.toDecimalString());
  }

  @TestTemplate
  void shouldHandleUnBlindedBlockContentsPostDeneb() throws Exception {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(DENEB);
    final BlockContents blockContents = dataStructureUtil.randomBlockContents(ONE);
    final BlockContainerAndMetaData<BlockContainer> blockContainerAndMetaData =
        new BlockContainerAndMetaData<>(
            blockContents,
            spec.getGenesisSpec().getMilestone(),
            executionPayloadValue,
            consensusBlockValue);
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
        .isEqualTo(executionPayloadValue.toDecimalString());
    assertThat(request.getResponseHeaders(HEADER_CONSENSUS_BLOCK_VALUE))
        .isEqualTo(consensusBlockValue.toDecimalString());
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
  void shouldSetExecutionPayloadValueToZeroWhenNoCachedPayloadResult()
      throws JsonProcessingException {
    final BlockContainer blockContainer;
    if (specMilestone.isGreaterThanOrEqualTo(DENEB)) {
      blockContainer = dataStructureUtil.randomBlockContents(ONE);
    } else {
      blockContainer = dataStructureUtil.randomBeaconBlock(ONE);
    }
    final ValidatorApiChannel validatorApiChannelMock = mock(ValidatorApiChannel.class);
    when(validatorApiChannelMock.createUnsignedBlock(any(), any(), any(), any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(blockContainer)));
    final CombinedChainDataClient combinedChainDataClientMock = mock(CombinedChainDataClient.class);
    when(combinedChainDataClientMock.getCurrentSlot()).thenReturn(ZERO);
    final BeaconState beaconStateMock = mock(BeaconState.class);
    when(combinedChainDataClientMock.getStateAtSlotExact(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(beaconStateMock)));
    final ExecutionLayerBlockProductionManager executionLayerBlockProductionManagerMock =
        mock(ExecutionLayerBlockProductionManager.class);
    when(executionLayerBlockProductionManagerMock.getCachedPayloadResult(any()))
        .thenReturn(Optional.empty());
    final RewardCalculator rewardCalculatorMock = mock(RewardCalculator.class);
    final BlockRewardData blockRewardDataMock = mock(BlockRewardData.class);
    when(blockRewardDataMock.getTotal()).thenReturn(consensusBlockValue.toLong());
    when(rewardCalculatorMock.getBlockRewardData(any(), any())).thenReturn(blockRewardDataMock);

    validatorDataProvider =
        new ValidatorDataProvider(
            spec,
            validatorApiChannelMock,
            combinedChainDataClientMock,
            executionLayerBlockProductionManagerMock,
            rewardCalculatorMock);

    try (final LogCaptor logCaptor = LogCaptor.forClass(ValidatorDataProvider.class)) {
      setHandler(new GetNewBlockV3(validatorDataProvider, schemaDefinitionCache));
      request.setPathParameter(SLOT, "1");
      request.setQueryParameter(RANDAO_REVEAL, signature.toBytesCompressed().toHexString());

      handler.handleRequest(request);
      assertThat(request.getResponseCode()).isEqualTo(SC_OK);

      final BlockContainerAndMetaData<BlockContainer> blockContainerAndMetaData =
          new BlockContainerAndMetaData<>(
              blockContainer,
              spec.getGenesisSpec().getMilestone(),
              UInt256.ZERO,
              consensusBlockValueWei);

      assertThat(request.getResponseBody()).isEqualTo(blockContainerAndMetaData);
      assertThat(request.getResponseHeaders(HEADER_CONSENSUS_BLOCK_VALUE))
          .isEqualTo(consensusBlockValueWei.toDecimalString());
      assertThat(request.getResponseHeaders(HEADER_EXECUTION_PAYLOAD_VALUE))
          .isEqualTo(UInt256.ZERO.toDecimalString());
      assertThat(logCaptor.getWarnLogs())
          .containsExactly(
              "Unable to get cached payload result for slot 1. Setting execution payload value to 0");
      assertThat(logCaptor.getThrowable(0)).isEmpty();
    }
  }

  @TestTemplate
  void shouldSetExecutionPayloadValueToZeroWhenNoExecutionPayloadValue()
      throws JsonProcessingException {
    final BlockContainer blockContainer;
    if (specMilestone.isGreaterThanOrEqualTo(DENEB)) {
      blockContainer = dataStructureUtil.randomBlockContents(ONE);
    } else {
      blockContainer = dataStructureUtil.randomBeaconBlock(ONE);
    }
    final ValidatorApiChannel validatorApiChannelMock = mock(ValidatorApiChannel.class);
    when(validatorApiChannelMock.createUnsignedBlock(any(), any(), any(), any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(blockContainer)));
    final CombinedChainDataClient combinedChainDataClientMock = mock(CombinedChainDataClient.class);
    when(combinedChainDataClientMock.getCurrentSlot()).thenReturn(ZERO);
    final BeaconState beaconStateMock = mock(BeaconState.class);
    when(combinedChainDataClientMock.getStateAtSlotExact(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(beaconStateMock)));
    final ExecutionLayerBlockProductionManager executionLayerBlockProductionManagerMock =
        mock(ExecutionLayerBlockProductionManager.class);
    final ExecutionPayloadResult executionPayloadResultMock = mock(ExecutionPayloadResult.class);
    when(executionPayloadResultMock.getExecutionPayloadValueFuture()).thenReturn(Optional.empty());
    when(executionLayerBlockProductionManagerMock.getCachedPayloadResult(any()))
        .thenReturn(Optional.of(executionPayloadResultMock));
    final RewardCalculator rewardCalculatorMock = mock(RewardCalculator.class);
    final BlockRewardData blockRewardDataMock = mock(BlockRewardData.class);
    when(blockRewardDataMock.getTotal()).thenReturn(consensusBlockValue.toLong());
    when(rewardCalculatorMock.getBlockRewardData(any(), any())).thenReturn(blockRewardDataMock);

    validatorDataProvider =
        new ValidatorDataProvider(
            spec,
            validatorApiChannelMock,
            combinedChainDataClientMock,
            executionLayerBlockProductionManagerMock,
            rewardCalculatorMock);

    try (final LogCaptor logCaptor = LogCaptor.forClass(ValidatorDataProvider.class)) {
      setHandler(new GetNewBlockV3(validatorDataProvider, schemaDefinitionCache));
      request.setPathParameter(SLOT, "1");
      request.setQueryParameter(RANDAO_REVEAL, signature.toBytesCompressed().toHexString());

      handler.handleRequest(request);
      assertThat(request.getResponseCode()).isEqualTo(SC_OK);

      final BlockContainerAndMetaData<BlockContainer> blockContainerAndMetaData =
          new BlockContainerAndMetaData<>(
              blockContainer,
              spec.getGenesisSpec().getMilestone(),
              UInt256.ZERO,
              consensusBlockValueWei);

      assertThat(request.getResponseBody()).isEqualTo(blockContainerAndMetaData);
      assertThat(request.getResponseHeaders(HEADER_CONSENSUS_BLOCK_VALUE))
          .isEqualTo(consensusBlockValueWei.toDecimalString());
      assertThat(request.getResponseHeaders(HEADER_EXECUTION_PAYLOAD_VALUE))
          .isEqualTo(UInt256.ZERO.toDecimalString());
      assertThat(logCaptor.getWarnLogs())
          .containsExactly("No execution payload value available for slot 1. Setting value to 0");
      assertThat(logCaptor.getThrowable(0)).isEmpty();
    }
  }

  @TestTemplate
  void shouldSetConsensusBlockRewardToZeroWhenUnableToCalculateIt() throws JsonProcessingException {
    final BlockContainer blockContainer;
    if (specMilestone.isGreaterThanOrEqualTo(DENEB)) {
      blockContainer = dataStructureUtil.randomBlockContents(ONE);
    } else {
      blockContainer = dataStructureUtil.randomBeaconBlock(ONE);
    }
    final ValidatorApiChannel validatorApiChannelMock = mock(ValidatorApiChannel.class);
    when(validatorApiChannelMock.createUnsignedBlock(any(), any(), any(), any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(blockContainer)));
    final CombinedChainDataClient combinedChainDataClientMock = mock(CombinedChainDataClient.class);
    when(combinedChainDataClientMock.getCurrentSlot()).thenReturn(ZERO);
    when(combinedChainDataClientMock.getStateAtSlotExact(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    final ExecutionLayerBlockProductionManager executionLayerBlockProductionManagerMock =
        mock(ExecutionLayerBlockProductionManager.class);
    final ExecutionPayloadResult executionPayloadResultMock = mock(ExecutionPayloadResult.class);
    when(executionPayloadResultMock.getExecutionPayloadValueFuture())
        .thenReturn(Optional.of(SafeFuture.completedFuture(executionPayloadValue)));
    when(executionLayerBlockProductionManagerMock.getCachedPayloadResult(any()))
        .thenReturn(Optional.of(executionPayloadResultMock));
    final RewardCalculator rewardCalculatorMock = mock(RewardCalculator.class);

    validatorDataProvider =
        new ValidatorDataProvider(
            spec,
            validatorApiChannelMock,
            combinedChainDataClientMock,
            executionLayerBlockProductionManagerMock,
            rewardCalculatorMock);

    try (final LogCaptor logCaptor = LogCaptor.forClass(ValidatorDataProvider.class)) {
      setHandler(new GetNewBlockV3(validatorDataProvider, schemaDefinitionCache));
      request.setPathParameter(SLOT, "1");
      request.setQueryParameter(RANDAO_REVEAL, signature.toBytesCompressed().toHexString());

      handler.handleRequest(request);
      assertThat(request.getResponseCode()).isEqualTo(SC_OK);

      final BlockContainerAndMetaData<BlockContainer> blockContainerAndMetaData =
          new BlockContainerAndMetaData<>(
              blockContainer,
              spec.getGenesisSpec().getMilestone(),
              executionPayloadValue,
              UInt256.ZERO);

      assertThat(request.getResponseBody()).isEqualTo(blockContainerAndMetaData);
      assertThat(request.getResponseHeaders(HEADER_CONSENSUS_BLOCK_VALUE))
          .isEqualTo(UInt256.ZERO.toDecimalString());
      assertThat(logCaptor.getWarnLogs())
          .containsExactly("Unable to calculate block rewards for slot 1. Setting value to 0");
      assertThat(logCaptor.getThrowable(0)).isEmpty();
    }
  }

  @TestTemplate
  void shouldSetConsensusBlockRewardToZeroPreAltair() throws Exception {
    assumeThat(specMilestone).isLessThan(ALTAIR);
    final BlockContainer blockContainer = dataStructureUtil.randomBeaconBlock(ONE);
    final ValidatorApiChannel validatorApiChannelMock = mock(ValidatorApiChannel.class);
    when(validatorApiChannelMock.createUnsignedBlock(any(), any(), any(), any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(blockContainer)));
    final BeaconState parentStateMock = mock(BeaconState.class);
    final CombinedChainDataClient combinedChainDataClientMock = mock(CombinedChainDataClient.class);
    when(combinedChainDataClientMock.getCurrentSlot()).thenReturn(ZERO);
    when(combinedChainDataClientMock.getStateAtSlotExact(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(parentStateMock)));
    final ExecutionLayerBlockProductionManager executionLayerBlockProductionManagerMock =
        mock(ExecutionLayerBlockProductionManager.class);
    final ExecutionPayloadResult executionPayloadResultMock = mock(ExecutionPayloadResult.class);
    when(executionPayloadResultMock.getExecutionPayloadValueFuture())
        .thenReturn(Optional.of(SafeFuture.completedFuture(executionPayloadValue)));
    when(executionLayerBlockProductionManagerMock.getCachedPayloadResult(any()))
        .thenReturn(Optional.of(executionPayloadResultMock));
    final RewardCalculator rewardCalculatorMock =
        new RewardCalculator(spec, new BlockRewardCalculatorUtil(spec));

    validatorDataProvider =
        new ValidatorDataProvider(
            spec,
            validatorApiChannelMock,
            combinedChainDataClientMock,
            executionLayerBlockProductionManagerMock,
            rewardCalculatorMock);

    try (final LogCaptor logCaptor = LogCaptor.forClass(ValidatorDataProvider.class)) {
      setHandler(new GetNewBlockV3(validatorDataProvider, schemaDefinitionCache));
      request.setPathParameter(SLOT, "1");
      request.setQueryParameter(RANDAO_REVEAL, signature.toBytesCompressed().toHexString());

      handler.handleRequest(request);
      assertThat(request.getResponseCode()).isEqualTo(SC_OK);

      final BlockContainerAndMetaData<BlockContainer> blockContainerAndMetaData =
          new BlockContainerAndMetaData<>(
              blockContainer,
              spec.getGenesisSpec().getMilestone(),
              executionPayloadValue,
              UInt256.ZERO);

      assertThat(request.getResponseBody()).isEqualTo(blockContainerAndMetaData);
      assertThat(request.getResponseHeaders(HEADER_CONSENSUS_BLOCK_VALUE))
          .isEqualTo(UInt256.ZERO.toDecimalString());
      assertThat(logCaptor.getWarnLogs())
          .containsExactly("Unable to calculate block rewards for slot 1. Setting value to 0");
      assertThat(logCaptor.getThrowable(0)).isPresent();
      assertThat(logCaptor.getThrowable(0).get().getCause())
          .isInstanceOf(BadRequestException.class);
      assertThat(logCaptor.getThrowable(0).get().getCause().getMessage())
          .isEqualTo(
              String.format(
                  "Slot %d is pre altair, and no sync committee information is available",
                  blockContainer.getSlot().intValue()));
    }
  }
}
