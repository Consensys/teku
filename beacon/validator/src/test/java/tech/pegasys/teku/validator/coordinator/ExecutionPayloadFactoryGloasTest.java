/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.validator.coordinator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.execution.BlobsBundle;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadResult;
import tech.pegasys.teku.spec.datastructures.execution.GetPayloadResponse;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerBlockProductionManager;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

class ExecutionPayloadFactoryGloasTest {

  private final Spec spec = TestSpecFactory.createMinimalGloas();

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final ExecutionLayerBlockProductionManager executionLayerBlockProductionManager =
      mock(ExecutionLayerBlockProductionManager.class);

  private BlobsBundle blobsBundle;
  private GetPayloadResponse getPayloadResponse;

  private final ExecutionPayloadFactoryGloas executionPayloadFactory =
      new ExecutionPayloadFactoryGloas(spec, executionLayerBlockProductionManager);

  @Test
  public void createsUnsignedExecutionPayload() {
    assertExecutionPayloadCreated(UInt64.ONE, 3, this::prepareValidGetPayloadResponse);
  }

  @Test
  public void createsDataColumnSidecars() {
    final UInt64 slot = UInt64.ONE;

    final SchemaDefinitionsGloas schemaDefinitions =
        SchemaDefinitionsGloas.required(spec.atSlot(slot).getSchemaDefinitions());
    final SpecConfigFulu specConfig = SpecConfigFulu.required(spec.atSlot(slot).getConfig());

    blobsBundle = dataStructureUtil.randomBlobsBundle(3);

    getPayloadResponse =
        new GetPayloadResponse(
            dataStructureUtil.randomExecutionPayload(slot),
            dataStructureUtil.randomUInt256(),
            blobsBundle,
            false,
            dataStructureUtil.randomExecutionRequests());

    setupCachingOfThePayloadResult(slot, getPayloadResponse);

    final ExecutionPayloadEnvelope executionPayload =
        dataStructureUtil.randomExecutionPayloadEnvelope(
            slot,
            schemaDefinitions.getBlobKzgCommitmentsSchema().createFromBlobsBundle(blobsBundle));

    final SignedExecutionPayloadEnvelope signedExecutionPayload =
        schemaDefinitions
            .getSignedExecutionPayloadEnvelopeSchema()
            .create(executionPayload, dataStructureUtil.randomSignature());

    final List<DataColumnSidecar> dataColumnSidecars =
        safeJoin(executionPayloadFactory.createDataColumnSidecars(signedExecutionPayload));

    assertThat(dataColumnSidecars.size()).isEqualTo(specConfig.getNumberOfColumns());
    dataColumnSidecars.forEach(
        dataColumnSidecar ->
            assertThat(dataColumnSidecar.getBeaconBlockRoot())
                .isEqualTo(executionPayload.getBeaconBlockRoot()));
  }

  private void assertExecutionPayloadCreated(
      final UInt64 slot,
      final int blobsCount,
      final Consumer<BeaconBlockAndState> getPayloadResponseBuilder) {
    final StorageSystem localChain = InMemoryStorageSystemBuilder.buildDefault(spec);

    localChain.chainUpdater().initializeGenesis(true);

    blobsBundle = dataStructureUtil.randomBlobsBundle(blobsCount);

    final SszList<SszKZGCommitment> kzgCommitments =
        dataStructureUtil.getBlobKzgCommitmentsSchema().createFromBlobsBundle(blobsBundle);
    final ChainBuilder.BlockOptions blockOptions =
        ChainBuilder.BlockOptions.create().setKzgCommitments(kzgCommitments);

    final BeaconBlockAndState blockAndState =
        localChain.chainUpdater().advanceChain(slot, blockOptions).toUnsigned();

    getPayloadResponseBuilder.accept(blockAndState);

    setupCachingOfThePayloadResult(slot, getPayloadResponse);

    final UInt64 builderIndex = getBidFromBlock(blockAndState.getBlock()).getBuilderIndex();

    final ExecutionPayloadEnvelope executionPayload =
        safeJoin(
            executionPayloadFactory.createUnsignedExecutionPayload(builderIndex, blockAndState));

    // result assertions
    assertThat(executionPayload.getPayload()).isEqualTo(getPayloadResponse.getExecutionPayload());
    assertThat(executionPayload.getExecutionRequests())
        .isEqualTo(getPayloadResponse.getExecutionRequests().orElseThrow());
    assertThat(executionPayload.getBuilderIndex()).isEqualTo(builderIndex);
    assertThat(executionPayload.getBeaconBlockRoot()).isEqualTo(blockAndState.getRoot());
    assertThat(executionPayload.getSlot()).isEqualTo(slot);
    assertThat(executionPayload.getBlobKzgCommitments()).isEqualTo(kzgCommitments);
    assertThat(executionPayload.getStateRoot()).isNotEqualTo(Bytes32.ZERO);
  }

  private void prepareValidGetPayloadResponse(final BeaconBlockAndState blockAndState) {
    final BeaconState state = blockAndState.getState();
    final SpecVersion specVersion = spec.atSlot(state.getSlot());
    final MiscHelpers miscHelpers = specVersion.miscHelpers();
    final ExecutionPayloadBid bid = getBidFromBlock(blockAndState.getBlock());
    final ExecutionPayload executionPayload =
        dataStructureUtil.randomExecutionPayload(
            state.getSlot(),
            builder ->
                builder
                    .parentHash(BeaconStateGloas.required(state).getLatestBlockHash())
                    .gasLimit(bid.getGasLimit())
                    .blockHash(bid.getBlockHash())
                    .prevRandao(bid.getPrevRandao())
                    .timestamp(
                        miscHelpers.computeTimeAtSlot(state.getGenesisTime(), state.getSlot()))
                    .withdrawals(Collections::emptyList));
    getPayloadResponse =
        new GetPayloadResponse(
            executionPayload,
            dataStructureUtil.randomUInt256(),
            blobsBundle,
            false,
            dataStructureUtil.randomExecutionRequests());
  }

  private ExecutionPayloadBid getBidFromBlock(final BeaconBlock block) {
    return block.getBody().getOptionalSignedExecutionPayloadBid().orElseThrow().getMessage();
  }

  private void setupCachingOfThePayloadResult(
      final UInt64 slot, final GetPayloadResponse getPayloadResponse) {
    // simulate caching of the payload result
    when(executionLayerBlockProductionManager.getCachedPayloadResult(slot))
        .thenReturn(
            Optional.of(
                ExecutionPayloadResult.createForLocalFlow(
                    dataStructureUtil.randomPayloadExecutionContext(slot, false),
                    SafeFuture.completedFuture(getPayloadResponse))));
  }
}
