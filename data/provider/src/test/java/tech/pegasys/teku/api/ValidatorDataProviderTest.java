/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.api;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.ssz.SszDataAssert.assertThatSszData;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult.FailureReason;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.api.response.v1.beacon.PostDataFailure;
import tech.pegasys.teku.api.response.v1.beacon.PostDataFailureResponse;
import tech.pegasys.teku.api.response.v1.validator.PostAttesterDutiesResponse;
import tech.pegasys.teku.api.schema.Attestation;
import tech.pegasys.teku.api.schema.BLSPubKey;
import tech.pegasys.teku.api.schema.BLSSignature;
import tech.pegasys.teku.api.schema.BeaconBlock;
import tech.pegasys.teku.api.schema.ValidatorBlockResult;
import tech.pegasys.teku.api.schema.altair.SignedBeaconBlockAltair;
import tech.pegasys.teku.api.schema.merge.SignedBeaconBlockMerge;
import tech.pegasys.teku.api.schema.phase0.SignedBeaconBlockPhase0;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SafeFutureAssert;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.ChainDataUnavailableException;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.validator.api.AttesterDuties;
import tech.pegasys.teku.validator.api.AttesterDuty;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.api.SubmitDataError;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;

@TestSpecContext(allMilestones = true)
public class ValidatorDataProviderTest {

  @SuppressWarnings("unchecked")
  private final ArgumentCaptor<List<tech.pegasys.teku.spec.datastructures.operations.Attestation>>
      args = ArgumentCaptor.forClass(List.class);

  private final JsonProvider jsonProvider = new JsonProvider();
  private Spec spec;
  private DataStructureUtil dataStructureUtil;
  private SchemaObjectProvider schemaProvider;
  private final CombinedChainDataClient combinedChainDataClient =
      mock(CombinedChainDataClient.class);
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private ValidatorDataProvider provider;
  private tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock blockInternal;
  private BeaconBlock block;
  private final tech.pegasys.teku.bls.BLSSignature signatureInternal =
      BLSTestUtil.randomSignature(1234);
  private final BLSSignature signature = new BLSSignature(signatureInternal);

  @BeforeEach
  @TestTemplate
  public void setup(SpecContext specContext) {
    spec = specContext.getSpec();
    dataStructureUtil = specContext.getDataStructureUtil();
    schemaProvider = new SchemaObjectProvider(spec);
    provider = new ValidatorDataProvider(spec, validatorApiChannel, combinedChainDataClient);
    blockInternal = dataStructureUtil.randomBeaconBlock(123);
    block = schemaProvider.getBeaconBlock(blockInternal);
  }

  @TestTemplate
  void getUnsignedBeaconBlockAtSlot_throwsWithoutSlotDefined() {
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> provider.getUnsignedBeaconBlockAtSlot(null, null, Optional.empty()));
  }

  @TestTemplate
  void getUnsignedBeaconBlockAtSlot_shouldThrowWithoutRandaoDefined() {
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> provider.getUnsignedBeaconBlockAtSlot(ONE, null, Optional.empty()));
  }

  @TestTemplate
  void getUnsignedBeaconBlockAtSlot_shouldThrowIfHistoricSlotRequested() {
    when(combinedChainDataClient.getCurrentSlot()).thenReturn(ONE);

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> provider.getUnsignedBeaconBlockAtSlot(ZERO, signature, Optional.empty()));
  }

  @TestTemplate
  void getUnsignedBeaconBlockAtSlot_shouldThrowIfFarFutureSlotRequested() {
    when(combinedChainDataClient.getCurrentSlot()).thenReturn(ONE);

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(
            () ->
                provider.getUnsignedBeaconBlockAtSlot(
                    UInt64.valueOf(10L), signature, Optional.empty()));
  }

  @TestTemplate
  void getUnsignedBeaconBlockAtSlot_shouldCreateAnUnsignedBlock() {
    when(combinedChainDataClient.getCurrentSlot()).thenReturn(ZERO);
    when(validatorApiChannel.createUnsignedBlock(ONE, signatureInternal, Optional.empty()))
        .thenReturn(completedFuture(Optional.of(blockInternal)));

    SafeFuture<Optional<BeaconBlock>> data =
        provider.getUnsignedBeaconBlockAtSlot(ONE, signature, Optional.empty());
    verify(validatorApiChannel).createUnsignedBlock(ONE, signatureInternal, Optional.empty());
    assertThat(data).isCompleted();
    assertThat(data.getNow(null).orElseThrow()).usingRecursiveComparison().isEqualTo(block);
  }

  @TestTemplate
  void getAttestationDataAtSlot_shouldThrowIfFutureSlotRequested() {
    when(combinedChainDataClient.isStoreAvailable()).thenReturn(true);
    when(validatorApiChannel.createAttestationData(ONE, 0))
        .thenReturn(SafeFuture.failedFuture(new IllegalArgumentException("Computer says no")));

    final SafeFuture<Optional<tech.pegasys.teku.api.schema.AttestationData>> result =
        provider.createAttestationDataAtSlot(ONE, 0);
    verify(validatorApiChannel).createAttestationData(ONE, 0);
    SafeFutureAssert.assertThatSafeFuture(result)
        .isCompletedExceptionallyWith(BadRequestException.class);
  }

  @TestTemplate
  void getAttestationDataAtSlot_shouldThrowIfStoreNotFound() {
    when(combinedChainDataClient.isStoreAvailable()).thenReturn(false);
    final SafeFuture<Optional<tech.pegasys.teku.api.schema.AttestationData>> result =
        provider.createAttestationDataAtSlot(ZERO, 0);
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::join).hasRootCauseInstanceOf(ChainDataUnavailableException.class);
    verify(combinedChainDataClient).isStoreAvailable();
  }

  @TestTemplate
  void getAttestationDataAtSlot_shouldReturnEmptyIfBlockNotFound() {
    when(combinedChainDataClient.isStoreAvailable()).thenReturn(true);
    when(validatorApiChannel.createAttestationData(ZERO, 0))
        .thenReturn(completedFuture(Optional.empty()));

    final SafeFuture<Optional<tech.pegasys.teku.api.schema.AttestationData>> result =
        provider.createAttestationDataAtSlot(ZERO, 0);
    verify(validatorApiChannel).createAttestationData(ZERO, 0);
    assertThat(result).isCompletedWithValue(Optional.empty());
  }

  @TestTemplate
  void parseBlock_shouldParseBlocks() throws JsonProcessingException {
    final SignedBeaconBlock internalSignedBlock = dataStructureUtil.randomSignedBeaconBlock(ONE);
    final tech.pegasys.teku.api.schema.SignedBeaconBlock signedBlock =
        schemaProvider.getSignedBeaconBlock(internalSignedBlock);
    final String signedBlockJson = jsonProvider.objectToJSON(signedBlock);

    final tech.pegasys.teku.api.schema.SignedBeaconBlock parsedBlock =
        provider.parseBlock(jsonProvider, signedBlockJson);

    assertThat(parsedBlock).isEqualTo(signedBlock);
    assertThat(parsedBlock).isInstanceOf(tech.pegasys.teku.api.schema.SignedBeaconBlock.class);
  }

  @TestTemplate
  void parseBlock_shouldParseMilestoneSpecificBlocks(SpecContext specContext)
      throws JsonProcessingException {
    final SignedBeaconBlock internalSignedBlock = dataStructureUtil.randomSignedBeaconBlock(ONE);
    final tech.pegasys.teku.api.schema.SignedBeaconBlock signedBlock =
        schemaProvider.getSignedBeaconBlock(internalSignedBlock);
    final String signedBlockJson = jsonProvider.objectToJSON(signedBlock);

    final tech.pegasys.teku.api.schema.SignedBeaconBlock parsedBlock =
        provider.parseBlock(jsonProvider, signedBlockJson);

    assertThat(parsedBlock).isEqualTo(signedBlock);
    switch (specContext.getSpecMilestone()) {
      case PHASE0:
        assertThat(parsedBlock).isInstanceOf(SignedBeaconBlockPhase0.class);
        break;
      case ALTAIR:
        assertThat(parsedBlock).isInstanceOf(SignedBeaconBlockAltair.class);
        break;
      case MERGE:
        assertThat(parsedBlock).isInstanceOf(SignedBeaconBlockMerge.class);
        break;
    }
  }

  @TestTemplate
  void getAttestationDataAtSlot_shouldReturnAttestationData() {
    when(combinedChainDataClient.isStoreAvailable()).thenReturn(true);
    final tech.pegasys.teku.spec.datastructures.operations.AttestationData internalData =
        dataStructureUtil.randomAttestationData();
    when(validatorApiChannel.createAttestationData(ONE, 0))
        .thenReturn(completedFuture(Optional.of(internalData)));

    final SafeFuture<Optional<tech.pegasys.teku.api.schema.AttestationData>> result =
        provider.createAttestationDataAtSlot(ONE, 0);
    assertThat(result).isCompleted();
    tech.pegasys.teku.api.schema.AttestationData data = result.join().orElseThrow();
    assertThat(data.index).isEqualTo(internalData.getIndex());
    assertThat(data.slot).isEqualTo(internalData.getSlot());
    assertThat(data.beacon_block_root).isEqualTo(internalData.getBeacon_block_root());
  }

  @TestTemplate
  void submitAttestation_shouldSubmitAnInternalAttestationStructure() {
    tech.pegasys.teku.spec.datastructures.operations.Attestation internalAttestation =
        dataStructureUtil.randomAttestation();
    Attestation attestation = new Attestation(internalAttestation);
    final List<SubmitDataError> errors = List.of(new SubmitDataError(ZERO, "Nope"));
    final SafeFuture<List<SubmitDataError>> result = SafeFuture.completedFuture(errors);
    when(validatorApiChannel.sendSignedAttestations(any())).thenReturn(result);

    assertThatSafeFuture(provider.submitAttestations(List.of(attestation)))
        .isCompletedWithOptionalContaining(
            new PostDataFailureResponse(
                SC_BAD_REQUEST,
                ValidatorDataProvider.PARTIAL_PUBLISH_FAILURE_MESSAGE,
                List.of(new PostDataFailure(ZERO, "Nope"))));

    verify(validatorApiChannel).sendSignedAttestations(args.capture());
    assertThat(args.getValue()).hasSize(1);
    assertThatSszData(args.getValue().get(0)).isEqualByAllMeansTo(internalAttestation);
  }

  @TestTemplate
  public void submitSignedBlock_shouldReturn200ForSuccess()
      throws ExecutionException, InterruptedException {
    final SignedBeaconBlock internalSignedBeaconBlock =
        dataStructureUtil.randomSignedBeaconBlock(1);
    final tech.pegasys.teku.api.schema.SignedBeaconBlock signedBeaconBlock =
        tech.pegasys.teku.api.schema.SignedBeaconBlock.create(internalSignedBeaconBlock);

    final SafeFuture<SendSignedBlockResult> successImportResult =
        completedFuture(SendSignedBlockResult.success(internalSignedBeaconBlock.getRoot()));

    when(validatorApiChannel.sendSignedBlock(any())).thenReturn(successImportResult);

    final SafeFuture<ValidatorBlockResult> validatorBlockResultSafeFuture =
        provider.submitSignedBlock(signedBeaconBlock);

    assertThat(validatorBlockResultSafeFuture.get().getResponseCode()).isEqualTo(200);
  }

  @TestTemplate
  public void submitSignedBlock_shouldReturn202ForInvalidBlock() {
    final SignedBeaconBlock internalSignedBeaconBlock =
        dataStructureUtil.randomSignedBeaconBlock(1);
    final tech.pegasys.teku.api.schema.SignedBeaconBlock signedBeaconBlock =
        tech.pegasys.teku.api.schema.SignedBeaconBlock.create(internalSignedBeaconBlock);
    final AtomicInteger failReasonCount = new AtomicInteger();

    Stream.of(FailureReason.values())
        .filter(failureReason -> !failureReason.equals(FailureReason.INTERNAL_ERROR))
        .forEach(
            failureReason -> {
              failReasonCount.getAndIncrement();

              final SafeFuture<SendSignedBlockResult> failImportResult =
                  completedFuture(SendSignedBlockResult.notImported(failureReason.name()));

              when(validatorApiChannel.sendSignedBlock(any())).thenReturn(failImportResult);

              final SafeFuture<ValidatorBlockResult> validatorBlockResultSafeFuture =
                  provider.submitSignedBlock(signedBeaconBlock);

              try {
                assertThat(validatorBlockResultSafeFuture.get().getResponseCode()).isEqualTo(202);
              } catch (final Exception e) {
                fail("Exception while executing test.");
              }
            });

    // Assert that the check has run over each FailureReason except the 500.
    assertThat(failReasonCount.get()).isEqualTo(FailureReason.values().length - 1);
  }

  @TestTemplate
  public void submitSignedBlock_shouldReturn500ForInternalError()
      throws ExecutionException, InterruptedException {
    final SignedBeaconBlock internalSignedBeaconBlock =
        dataStructureUtil.randomSignedBeaconBlock(1);
    final tech.pegasys.teku.api.schema.SignedBeaconBlock signedBeaconBlock =
        tech.pegasys.teku.api.schema.SignedBeaconBlock.create(internalSignedBeaconBlock);

    final SafeFuture<SendSignedBlockResult> failImportResult =
        completedFuture(SendSignedBlockResult.rejected(FailureReason.INTERNAL_ERROR.name()));

    when(validatorApiChannel.sendSignedBlock(any())).thenReturn(failImportResult);

    final SafeFuture<ValidatorBlockResult> validatorBlockResultSafeFuture =
        provider.submitSignedBlock(signedBeaconBlock);

    assertThat(validatorBlockResultSafeFuture.get().getResponseCode()).isEqualTo(500);
  }

  @TestTemplate
  public void getAttesterDuties_shouldHandleEmptyIndexesList() {
    final Bytes32 previousTargetRoot = dataStructureUtil.randomBytes32();
    when(validatorApiChannel.getAttestationDuties(eq(ONE), any()))
        .thenReturn(
            completedFuture(
                Optional.of(
                    new tech.pegasys.teku.validator.api.AttesterDuties(
                        previousTargetRoot, emptyList()))));
    final SafeFuture<Optional<PostAttesterDutiesResponse>> future =
        provider.getAttesterDuties(UInt64.ONE, List.of());
    assertThat(future).isCompleted();
    Optional<PostAttesterDutiesResponse> maybeData = future.join();
    assertThat(maybeData.isPresent()).isTrue();
    assertThat(maybeData.get().data).isEmpty();
  }

  @TestTemplate
  public void getAttesterDuties_shouldReturnDutiesForKnownValidator() {
    AttesterDuty v1 = new AttesterDuty(BLSTestUtil.randomPublicKey(0), 1, 2, 3, 15, 4, ONE);
    AttesterDuty v2 = new AttesterDuty(BLSTestUtil.randomPublicKey(1), 11, 12, 13, 15, 14, ZERO);
    when(validatorApiChannel.getAttestationDuties(eq(ONE), any()))
        .thenReturn(
            completedFuture(
                Optional.of(
                    new AttesterDuties(dataStructureUtil.randomBytes32(), List.of(v1, v2)))));

    final SafeFuture<Optional<PostAttesterDutiesResponse>> future =
        provider.getAttesterDuties(ONE, List.of(1, 11));
    assertThat(future).isCompleted();
    final Optional<PostAttesterDutiesResponse> maybeList = future.join();
    final PostAttesterDutiesResponse list = maybeList.orElseThrow();
    assertThat(list.data).containsExactlyInAnyOrder(asAttesterDuty(v1), asAttesterDuty(v2));
  }

  private tech.pegasys.teku.api.response.v1.validator.AttesterDuty asAttesterDuty(
      final AttesterDuty duties) {

    return new tech.pegasys.teku.api.response.v1.validator.AttesterDuty(
        new BLSPubKey(duties.getPublicKey()),
        UInt64.valueOf(duties.getValidatorIndex()),
        UInt64.valueOf(duties.getCommitteeIndex()),
        UInt64.valueOf(duties.getCommitteeLength()),
        UInt64.valueOf(duties.getCommitteesAtSlot()),
        UInt64.valueOf(duties.getValidatorCommitteeIndex()),
        duties.getSlot());
  }
}
