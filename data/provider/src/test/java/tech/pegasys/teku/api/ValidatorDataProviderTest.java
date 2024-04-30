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

package tech.pegasys.teku.api;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.infrastructure.ssz.SszDataAssert.assertThatSszData;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult.FailureReason;

import com.fasterxml.jackson.core.JsonProcessingException;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorStatus;
import tech.pegasys.teku.api.schema.ValidatorBlockResult;
import tech.pegasys.teku.api.schema.altair.SignedBeaconBlockAltair;
import tech.pegasys.teku.api.schema.bellatrix.SignedBeaconBlockBellatrix;
import tech.pegasys.teku.api.schema.capella.SignedBeaconBlockCapella;
import tech.pegasys.teku.api.schema.deneb.SignedBeaconBlockDeneb;
import tech.pegasys.teku.api.schema.eip7594.SignedBeaconBlockEip7594;
import tech.pegasys.teku.api.schema.phase0.SignedBeaconBlockPhase0;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.ethereum.json.types.validator.AttesterDuties;
import tech.pegasys.teku.ethereum.json.types.validator.AttesterDuty;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SafeFutureAssert;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.metadata.BlockContainerAndMetaData;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.ChainDataUnavailableException;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.api.SubmitDataError;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;

@TestSpecContext(allMilestones = true)
public class ValidatorDataProviderTest {

  @SuppressWarnings("unchecked")
  private final ArgumentCaptor<List<Attestation>> args = ArgumentCaptor.forClass(List.class);

  private final JsonProvider jsonProvider = new JsonProvider();
  private Spec spec;
  private SpecMilestone specMilestone;
  private DataStructureUtil dataStructureUtil;
  private SchemaObjectProvider schemaProvider;
  private final CombinedChainDataClient combinedChainDataClient =
      mock(CombinedChainDataClient.class);
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private ValidatorDataProvider provider;
  private BlockContainerAndMetaData blockContainerAndMetaDataInternal;
  private final BLSSignature signatureInternal = BLSTestUtil.randomSignature(1234);

  @BeforeEach
  public void setup(SpecContext specContext) {
    spec = specContext.getSpec();
    dataStructureUtil = specContext.getDataStructureUtil();
    schemaProvider = new SchemaObjectProvider(spec);
    provider = new ValidatorDataProvider(spec, validatorApiChannel, combinedChainDataClient);
    blockContainerAndMetaDataInternal = dataStructureUtil.randomBlockContainerAndMetaData(123);
    specMilestone = specContext.getSpecMilestone();
  }

  @TestTemplate
  void getUnsignedBeaconBlockAtSlot_throwsWithoutSlotDefined() {
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(
            () ->
                provider.getUnsignedBeaconBlockAtSlot(
                    null, null, Optional.empty(), false, Optional.empty()));
  }

  @TestTemplate
  void getUnsignedBeaconBlockAtSlot_shouldThrowWithoutRandaoDefined() {
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(
            () ->
                provider.getUnsignedBeaconBlockAtSlot(
                    ONE, null, Optional.empty(), false, Optional.empty()));
  }

  @TestTemplate
  void getUnsignedBeaconBlockAtSlot_shouldThrowIfHistoricSlotRequested() {
    when(combinedChainDataClient.getCurrentSlot()).thenReturn(ONE);

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(
            () ->
                provider.getUnsignedBeaconBlockAtSlot(
                    ZERO, signatureInternal, Optional.empty(), false, Optional.empty()));
  }

  @TestTemplate
  void getUnsignedBeaconBlockAtSlot_shouldThrowIfFarFutureSlotRequested() {
    when(combinedChainDataClient.getCurrentSlot()).thenReturn(ONE);

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(
            () ->
                provider.getUnsignedBeaconBlockAtSlot(
                    UInt64.valueOf(10L),
                    signatureInternal,
                    Optional.empty(),
                    false,
                    Optional.empty()));
  }

  @TestTemplate
  void getUnsignedBeaconBlockAtSlot_PreDeneb_shouldCreateAnUnsignedBlock() {
    assumeThat(specMilestone).isLessThan(SpecMilestone.DENEB);
    when(combinedChainDataClient.getCurrentSlot()).thenReturn(ZERO);
    when(validatorApiChannel.createUnsignedBlock(
            ONE, signatureInternal, Optional.empty(), Optional.of(false), Optional.empty()))
        .thenReturn(completedFuture(Optional.of(blockContainerAndMetaDataInternal)));

    SafeFuture<? extends Optional<BlockContainerAndMetaData>> data =
        provider.getUnsignedBeaconBlockAtSlot(
            ONE, signatureInternal, Optional.empty(), false, Optional.empty());

    verify(validatorApiChannel)
        .createUnsignedBlock(
            ONE, signatureInternal, Optional.empty(), Optional.of(false), Optional.empty());

    assertThat(data).isCompleted();

    assertThat(data.getNow(null).orElseThrow())
        .usingRecursiveComparison()
        .isEqualTo(blockContainerAndMetaDataInternal);
  }

  @TestTemplate
  void produceBlock_throwsWithoutSlotDefined() {
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> provider.produceBlock(null, null, Optional.empty(), Optional.empty()));
  }

  @TestTemplate
  void produceBlock_shouldThrowWithoutRandaoDefined() {
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> provider.produceBlock(ONE, null, Optional.empty(), Optional.empty()));
  }

  @TestTemplate
  void produceBlock_shouldThrowIfHistoricSlotRequested() {
    when(combinedChainDataClient.getCurrentSlot()).thenReturn(ONE);

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(
            () ->
                provider.produceBlock(ZERO, signatureInternal, Optional.empty(), Optional.empty()));
  }

  @TestTemplate
  void produceBlock_shouldThrowIfFarFutureSlotRequested() {
    when(combinedChainDataClient.getCurrentSlot()).thenReturn(ONE);

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(
            () ->
                provider.produceBlock(
                    UInt64.valueOf(10L), signatureInternal, Optional.empty(), Optional.empty()));
  }

  @TestTemplate
  void produceBlock_shouldCreateAnUnsignedBlock() {
    when(combinedChainDataClient.getCurrentSlot()).thenReturn(ONE);
    when(combinedChainDataClient.getStateAtSlotExact(
            blockContainerAndMetaDataInternal.blockContainer().getSlot().decrement()))
        .thenReturn(completedFuture(Optional.of(dataStructureUtil.randomBeaconState())));

    when(validatorApiChannel.createUnsignedBlock(
            ONE, signatureInternal, Optional.empty(), Optional.empty(), Optional.of(ONE)))
        .thenReturn(completedFuture(Optional.of(blockContainerAndMetaDataInternal)));

    SafeFuture<? extends Optional<? extends BlockContainerAndMetaData>> data =
        provider.produceBlock(ONE, signatureInternal, Optional.empty(), Optional.of(ONE));

    verify(validatorApiChannel)
        .createUnsignedBlock(
            ONE, signatureInternal, Optional.empty(), Optional.empty(), Optional.of(ONE));

    assertThat(data).isCompleted();

    assertThat(data.getNow(null).orElseThrow())
        .usingRecursiveComparison()
        .isEqualTo(blockContainerAndMetaDataInternal);
  }

  @TestTemplate
  void getAttestationDataAtSlot_shouldThrowIfFutureSlotRequested() {
    when(combinedChainDataClient.isStoreAvailable()).thenReturn(true);
    when(validatorApiChannel.createAttestationData(ONE, 0))
        .thenReturn(SafeFuture.failedFuture(new IllegalArgumentException("Computer says no")));

    final SafeFuture<Optional<AttestationData>> result =
        provider.createAttestationDataAtSlot(ONE, 0);
    verify(validatorApiChannel).createAttestationData(ONE, 0);
    SafeFutureAssert.assertThatSafeFuture(result)
        .isCompletedExceptionallyWith(BadRequestException.class);
  }

  @TestTemplate
  void getAttestationDataAtSlot_shouldThrowIfStoreNotFound() {
    when(combinedChainDataClient.isStoreAvailable()).thenReturn(false);
    final SafeFuture<Optional<AttestationData>> result =
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

    final SafeFuture<Optional<AttestationData>> result =
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
  void parseBlock_shouldParseBlindedBlocks() throws JsonProcessingException {
    final SignedBeaconBlock internalSignedBlock =
        dataStructureUtil.randomSignedBlindedBeaconBlock(ONE);
    final tech.pegasys.teku.api.schema.SignedBeaconBlock signedBlock =
        schemaProvider.getSignedBlindedBeaconBlock(internalSignedBlock);
    final String signedBlockJson = jsonProvider.objectToJSON(signedBlock);

    final tech.pegasys.teku.api.schema.SignedBeaconBlock parsedBlock =
        provider.parseBlindedBlock(jsonProvider, signedBlockJson);

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
      case BELLATRIX:
        assertThat(parsedBlock).isInstanceOf(SignedBeaconBlockBellatrix.class);
        break;
      case CAPELLA:
        assertThat(parsedBlock).isInstanceOf(SignedBeaconBlockCapella.class);
        break;
      case DENEB:
        assertThat(parsedBlock).isInstanceOf(SignedBeaconBlockDeneb.class);
        break;
      case EIP7594:
        assertThat(parsedBlock).isInstanceOf(SignedBeaconBlockEip7594.class);
        break;
      default:
        throw new RuntimeException("notImplemented");
    }
  }

  @TestTemplate
  void getAttestationDataAtSlot_shouldReturnAttestationData() {
    when(combinedChainDataClient.isStoreAvailable()).thenReturn(true);
    final AttestationData internalData = dataStructureUtil.randomAttestationData();
    when(validatorApiChannel.createAttestationData(ONE, 0))
        .thenReturn(completedFuture(Optional.of(internalData)));

    final SafeFuture<Optional<AttestationData>> result =
        provider.createAttestationDataAtSlot(ONE, 0);
    assertThat(result).isCompleted();
    AttestationData data = safeJoin(result).orElseThrow();
    assertThat(data.getIndex()).isEqualTo(internalData.getIndex());
    assertThat(data.getSlot()).isEqualTo(internalData.getSlot());
    assertThat(data.getBeaconBlockRoot()).isEqualTo(internalData.getBeaconBlockRoot());
  }

  @TestTemplate
  void submitAttestation_shouldSubmitAnInternalAttestationStructure() {
    Attestation attestation = dataStructureUtil.randomAttestation();
    final List<SubmitDataError> errors = List.of(new SubmitDataError(ZERO, "Nope"));
    when(validatorApiChannel.sendSignedAttestations(any()))
        .thenReturn(SafeFuture.completedFuture(errors));

    assertThatSafeFuture(provider.submitAttestations(List.of(attestation)))
        .isCompletedWithValue(errors);

    verify(validatorApiChannel).sendSignedAttestations(args.capture());
    assertThat(args.getValue()).hasSize(1);
    assertThatSszData(args.getValue().get(0)).isEqualByAllMeansTo(attestation);
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

    when(validatorApiChannel.sendSignedBlock(any(), any())).thenReturn(successImportResult);

    final SafeFuture<ValidatorBlockResult> validatorBlockResultSafeFuture =
        provider.submitSignedBlock(signedBeaconBlock, BroadcastValidationLevel.NOT_REQUIRED);

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

              when(validatorApiChannel.sendSignedBlock(any(), any())).thenReturn(failImportResult);

              final SafeFuture<ValidatorBlockResult> validatorBlockResultSafeFuture =
                  provider.submitSignedBlock(
                      signedBeaconBlock, BroadcastValidationLevel.NOT_REQUIRED);

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

    when(validatorApiChannel.sendSignedBlock(any(), any())).thenReturn(failImportResult);

    final SafeFuture<ValidatorBlockResult> validatorBlockResultSafeFuture =
        provider.submitSignedBlock(signedBeaconBlock, BroadcastValidationLevel.NOT_REQUIRED);

    assertThat(validatorBlockResultSafeFuture.get().getResponseCode()).isEqualTo(500);
  }

  @TestTemplate
  public void getAttesterDuties_shouldHandleEmptyIndicesList() {
    final Bytes32 previousTargetRoot = dataStructureUtil.randomBytes32();
    when(validatorApiChannel.getAttestationDuties(eq(ONE), any()))
        .thenReturn(
            completedFuture(
                Optional.of(new AttesterDuties(false, previousTargetRoot, emptyList()))));
    final SafeFuture<Optional<AttesterDuties>> future =
        provider.getAttesterDuties(UInt64.ONE, IntList.of());
    assertThat(future).isCompleted();
    Optional<AttesterDuties> maybeData = safeJoin(future);
    assertThat(maybeData.isPresent()).isTrue();
    assertThat(maybeData.get().getDuties()).isEmpty();
  }

  @TestTemplate
  public void getAttesterDuties_shouldReturnDutiesForKnownValidator() {
    AttesterDuty v1 = new AttesterDuty(BLSTestUtil.randomPublicKey(0), 1, 2, 3, 15, 4, ONE);
    AttesterDuty v2 = new AttesterDuty(BLSTestUtil.randomPublicKey(1), 11, 12, 13, 15, 14, ZERO);
    when(validatorApiChannel.getAttestationDuties(eq(ONE), any()))
        .thenReturn(
            completedFuture(
                Optional.of(
                    new AttesterDuties(
                        false, dataStructureUtil.randomBytes32(), List.of(v1, v2)))));

    final SafeFuture<Optional<AttesterDuties>> future =
        provider.getAttesterDuties(ONE, IntList.of(1, 11));
    assertThat(future).isCompleted();
    final Optional<AttesterDuties> maybeList = safeJoin(future);
    final AttesterDuties list = maybeList.orElseThrow();
    assertThat(list.getDuties()).containsExactlyInAnyOrder(v1, v2);
  }

  @TestTemplate
  void registerValidators_shouldReportErrorIfCannotRetrieveValidatorStatuses() {
    final SszList<SignedValidatorRegistration> validatorRegistrations =
        dataStructureUtil.randomSignedValidatorRegistrations(4);

    when(validatorApiChannel.getValidatorStatuses(anyCollection()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    final SafeFuture<Void> result = provider.registerValidators(validatorRegistrations);

    SafeFutureAssert.assertThatSafeFuture(result)
        .isCompletedExceptionallyWithMessage(
            "Couldn't retrieve validator statuses during registering. Most likely the BN is still syncing.");
  }

  @TestTemplate
  void registerValidators_shouldIgnoreExitedAndUnknownValidators() {
    final int numOfValidatorRegistrationsAttempted = ValidatorStatus.values().length + 2;

    final SszList<SignedValidatorRegistration> validatorRegistrations =
        dataStructureUtil.randomSignedValidatorRegistrations(numOfValidatorRegistrationsAttempted);

    final Map<BLSPublicKey, ValidatorStatus> knownValidators =
        IntStream.range(0, ValidatorStatus.values().length)
            .mapToObj(
                statusIdx ->
                    Map.entry(
                        validatorRegistrations.get(statusIdx).getMessage().getPublicKey(),
                        ValidatorStatus.values()[statusIdx]))
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));

    @SuppressWarnings("EnumOrdinal")
    final List<BLSPublicKey> exitedOrUnknownKeys =
        IntStream.range(
                ValidatorStatus.exited_unslashed.ordinal(), numOfValidatorRegistrationsAttempted)
            .mapToObj(
                statusOrdinal ->
                    validatorRegistrations.get(statusOrdinal).getMessage().getPublicKey())
            .toList();

    when(validatorApiChannel.getValidatorStatuses(anyCollection()))
        .thenAnswer(
            args -> {
              final Collection<BLSPublicKey> publicKeys = args.getArgument(0);
              final Map<BLSPublicKey, ValidatorStatus> validatorStatuses =
                  publicKeys.stream()
                      .filter(knownValidators::containsKey)
                      .collect(Collectors.toMap(Function.identity(), knownValidators::get));
              return SafeFuture.completedFuture(Optional.of(validatorStatuses));
            });
    when(validatorApiChannel.registerValidators(any())).thenReturn(SafeFuture.COMPLETE);

    final SafeFuture<Void> result = provider.registerValidators(validatorRegistrations);

    assertThat(result).isCompleted();

    @SuppressWarnings("unchecked")
    final ArgumentCaptor<SszList<SignedValidatorRegistration>> argumentCaptor =
        ArgumentCaptor.forClass(SszList.class);

    verify(validatorApiChannel).registerValidators(argumentCaptor.capture());

    final SszList<SignedValidatorRegistration> capturedRegistrations = argumentCaptor.getValue();

    assertThat(capturedRegistrations)
        .hasSize(5)
        .map(signedRegistration -> signedRegistration.getMessage().getPublicKey())
        .doesNotContainAnyElementsOf(exitedOrUnknownKeys);
  }
}
