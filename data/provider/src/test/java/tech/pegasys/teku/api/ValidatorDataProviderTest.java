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
import static tech.pegasys.teku.core.results.BlockImportResult.FailureReason;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.api.response.v1.validator.AttesterDuty;
import tech.pegasys.teku.api.schema.Attestation;
import tech.pegasys.teku.api.schema.BLSPubKey;
import tech.pegasys.teku.api.schema.BLSSignature;
import tech.pegasys.teku.api.schema.BeaconBlock;
import tech.pegasys.teku.api.schema.BeaconState;
import tech.pegasys.teku.api.schema.ValidatorBlockResult;
import tech.pegasys.teku.api.schema.ValidatorDuties;
import tech.pegasys.teku.api.schema.ValidatorDutiesRequest;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.operations.AttestationData;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.Bitlist;
import tech.pegasys.teku.storage.client.ChainDataUnavailableException;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.util.config.Constants;
import tech.pegasys.teku.validator.api.AttesterDuties;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;

public class ValidatorDataProviderTest {

  private final ArgumentCaptor<tech.pegasys.teku.datastructures.operations.Attestation> args =
      ArgumentCaptor.forClass(tech.pegasys.teku.datastructures.operations.Attestation.class);

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private CombinedChainDataClient combinedChainDataClient = mock(CombinedChainDataClient.class);
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private ValidatorDataProvider provider =
      new ValidatorDataProvider(validatorApiChannel, combinedChainDataClient);
  private final tech.pegasys.teku.datastructures.blocks.BeaconBlock blockInternal =
      dataStructureUtil.randomBeaconBlock(123);
  private final BeaconBlock block = new BeaconBlock(blockInternal);
  private final tech.pegasys.teku.bls.BLSSignature signatureInternal =
      tech.pegasys.teku.bls.BLSSignature.random(1234);
  private final BLSSignature signature = new BLSSignature(signatureInternal);
  private final tech.pegasys.teku.datastructures.state.BeaconState beaconStateInternal =
      dataStructureUtil.randomBeaconState();
  private final BeaconState beaconState = new BeaconState(beaconStateInternal);

  @Test
  void getUnsignedBeaconBlockAtSlot_throwsWithoutSlotDefined() {
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> provider.getUnsignedBeaconBlockAtSlot(null, null, Optional.empty()));
  }

  @Test
  void getUnsignedBeaconBlockAtSlot_shouldThrowWithoutRandaoDefined() {
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> provider.getUnsignedBeaconBlockAtSlot(ONE, null, Optional.empty()));
  }

  @Test
  void getUnsignedBeaconBlockAtSlot_shouldThrowIfHistoricSlotRequested() {
    when(combinedChainDataClient.getCurrentSlot()).thenReturn(ONE);

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> provider.getUnsignedBeaconBlockAtSlot(ZERO, signature, Optional.empty()));
  }

  @Test
  void getUnsignedBeaconBlockAtSlot_shouldThrowIfFarFutureSlotRequested() {
    when(combinedChainDataClient.getCurrentSlot()).thenReturn(ONE);

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(
            () ->
                provider.getUnsignedBeaconBlockAtSlot(
                    UInt64.valueOf(10L), signature, Optional.empty()));
  }

  @Test
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

  @Test
  void getUnsignedAttestationAtSlot_shouldThrowIfStoreNotFound() {
    when(combinedChainDataClient.isStoreAvailable()).thenReturn(false);
    final SafeFuture<Optional<Attestation>> result =
        provider.createUnsignedAttestationAtSlot(ZERO, 0);
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::join).hasRootCauseInstanceOf(ChainDataUnavailableException.class);
    verify(combinedChainDataClient).isStoreAvailable();
  }

  @Test
  void getUnsignedAttestationAtSlot_shouldReturnEmptyIfBlockNotFound() {
    when(combinedChainDataClient.isStoreAvailable()).thenReturn(true);
    when(validatorApiChannel.createUnsignedAttestation(ZERO, 0))
        .thenReturn(completedFuture(Optional.empty()));

    final SafeFuture<Optional<Attestation>> result =
        provider.createUnsignedAttestationAtSlot(ZERO, 0);
    verify(validatorApiChannel).createUnsignedAttestation(ZERO, 0);
    assertThat(result).isCompletedWithValue(Optional.empty());
  }

  @Test
  void getUnsignedAttestationAtSlot_shouldReturnAttestation() throws Exception {
    when(combinedChainDataClient.isStoreAvailable()).thenReturn(true);
    final tech.pegasys.teku.datastructures.operations.Attestation internalAttestation =
        dataStructureUtil.randomAttestation();
    when(validatorApiChannel.createUnsignedAttestation(ONE, 0))
        .thenReturn(completedFuture(Optional.of(internalAttestation)));

    final SafeFuture<Optional<Attestation>> result =
        provider.createUnsignedAttestationAtSlot(ONE, 0);
    assertThat(result).isCompleted();
    Attestation attestation = result.join().orElseThrow();
    assertThat(attestation.data.index).isEqualTo(internalAttestation.getData().getIndex());
    assertThat(attestation.signature.toHexString())
        .isEqualTo(internalAttestation.getAggregate_signature().toSSZBytes().toHexString());
    assertThat(attestation.data.slot).isEqualTo(internalAttestation.getData().getSlot());
    assertThat(attestation.data.beacon_block_root)
        .isEqualTo(internalAttestation.getData().getBeacon_block_root());
  }

  @Test
  void getValidatorsDutiesByRequest_shouldIncludeMissingValidators()
      throws ExecutionException, InterruptedException {
    when(combinedChainDataClient.isStoreAvailable()).thenReturn(true);
    when(combinedChainDataClient.getBestBlockRoot())
        .thenReturn(Optional.of(dataStructureUtil.randomBytes32()));
    final BLSPublicKey publicKey = dataStructureUtil.randomPublicKey();
    ValidatorDutiesRequest smallRequest =
        new ValidatorDutiesRequest(
            compute_epoch_at_slot(beaconState.slot),
            List.of(new BLSPubKey(publicKey.toBytesCompressed())));
    when(validatorApiChannel.getDuties(smallRequest.epoch, List.of(publicKey)))
        .thenReturn(
            completedFuture(
                Optional.of(
                    List.of(tech.pegasys.teku.validator.api.ValidatorDuties.noDuties(publicKey)))));

    SafeFuture<Optional<List<ValidatorDuties>>> future =
        provider.getValidatorDutiesByRequest(smallRequest);
    assertThat(future.get().get()).isNotEmpty();
    List<ValidatorDuties> validatorDuties = future.get().get();

    assertThat(validatorDuties.size()).isEqualTo(1);
    ValidatorDuties expected =
        new ValidatorDuties(
            new BLSPubKey(publicKey.toBytesCompressed()),
            null,
            null,
            null,
            null,
            emptyList(),
            null);
    assertThat(validatorDuties.get(0)).isEqualToComparingFieldByField(expected);
  }

  @Test
  void getValidatorDutiesByRequest_shouldIncludeValidatorDuties()
      throws ExecutionException, InterruptedException {
    when(combinedChainDataClient.isStoreAvailable()).thenReturn(true);
    when(combinedChainDataClient.getBestBlockRoot())
        .thenReturn(Optional.of(dataStructureUtil.randomBytes32()));
    final BLSPublicKey publicKey = dataStructureUtil.randomPublicKey();
    ValidatorDutiesRequest smallRequest =
        new ValidatorDutiesRequest(
            compute_epoch_at_slot(beaconState.slot),
            List.of(new BLSPubKey(publicKey.toBytesCompressed())));
    final int validatorIndex = 4;
    final int attestationCommitteeIndex = 2;
    final int attestationCommitteePosition = 5;
    final int aggregatorModulo = 12;
    final List<UInt64> blockProposalSlots = List.of(UInt64.valueOf(66), UInt64.valueOf(77));
    final UInt64 attestationSlot = UInt64.valueOf(50);
    when(validatorApiChannel.getDuties(smallRequest.epoch, List.of(publicKey)))
        .thenReturn(
            completedFuture(
                Optional.of(
                    List.of(
                        tech.pegasys.teku.validator.api.ValidatorDuties.withDuties(
                            publicKey,
                            validatorIndex,
                            attestationCommitteeIndex,
                            attestationCommitteePosition,
                            aggregatorModulo,
                            blockProposalSlots,
                            attestationSlot)))));

    SafeFuture<Optional<List<ValidatorDuties>>> future =
        provider.getValidatorDutiesByRequest(smallRequest);
    assertThat(future.get().get()).isNotEmpty();
    List<ValidatorDuties> validatorDuties = future.get().get();

    assertThat(validatorDuties.size()).isEqualTo(1);
    ValidatorDuties expected =
        new ValidatorDuties(
            new BLSPubKey(publicKey.toBytesCompressed()),
            validatorIndex,
            attestationCommitteeIndex,
            attestationCommitteePosition,
            aggregatorModulo,
            blockProposalSlots,
            attestationSlot);
    assertThat(validatorDuties.get(0)).isEqualToComparingFieldByField(expected);
  }

  @Test
  void getValidatorDutiesByRequest_shouldReturnChainDataUnavailableExceptionWhenStoreIsNotSet() {
    when(combinedChainDataClient.isStoreAvailable()).thenReturn(false);

    final SafeFuture<Optional<List<ValidatorDuties>>> result =
        provider.getValidatorDutiesByRequest(
            new ValidatorDutiesRequest(ONE, generatePublicKeys(1)));

    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::join).hasRootCauseInstanceOf(ChainDataUnavailableException.class);
  }

  @Test
  void submitAttestation_shouldSubmitAnInternalAttestationStructure() {
    tech.pegasys.teku.datastructures.operations.Attestation internalAttestation =
        dataStructureUtil.randomAttestation();
    Attestation attestation = new Attestation(internalAttestation);

    provider.submitAttestation(attestation);

    verify(validatorApiChannel).sendSignedAttestation(args.capture());
    assertThat(args.getValue()).usingRecursiveComparison().isEqualTo(internalAttestation);
  }

  @Test
  public void submitAttestation_shouldThrowIllegalArgumentExceptionWhenSignatureIsEmpty() {
    final AttestationData attestationData = dataStructureUtil.randomAttestationData();
    final tech.pegasys.teku.datastructures.operations.Attestation internalAttestation =
        new tech.pegasys.teku.datastructures.operations.Attestation(
            new Bitlist(4, Constants.MAX_VALIDATORS_PER_COMMITTEE),
            attestationData,
            tech.pegasys.teku.bls.BLSSignature.empty());

    final Attestation attestation = new Attestation(internalAttestation);

    assertThatThrownBy(() -> provider.submitAttestation(attestation))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void submitSignedBlock_shouldReturn200ForSuccess()
      throws ExecutionException, InterruptedException {
    final SignedBeaconBlock internalSignedBeaconBlock =
        dataStructureUtil.randomSignedBeaconBlock(1);
    final tech.pegasys.teku.api.schema.SignedBeaconBlock signedBeaconBlock =
        new tech.pegasys.teku.api.schema.SignedBeaconBlock(internalSignedBeaconBlock);

    final SafeFuture<SendSignedBlockResult> successImportResult =
        completedFuture(SendSignedBlockResult.success(internalSignedBeaconBlock.getRoot()));

    when(validatorApiChannel.sendSignedBlock(any())).thenReturn(successImportResult);

    final SafeFuture<ValidatorBlockResult> validatorBlockResultSafeFuture =
        provider.submitSignedBlock(signedBeaconBlock);

    assertThat(validatorBlockResultSafeFuture.get().getResponseCode()).isEqualTo(200);
  }

  @Test
  public void submitSignedBlock_shouldReturn202ForInvalidBlock() {
    final SignedBeaconBlock internalSignedBeaconBlock =
        dataStructureUtil.randomSignedBeaconBlock(1);
    final tech.pegasys.teku.api.schema.SignedBeaconBlock signedBeaconBlock =
        new tech.pegasys.teku.api.schema.SignedBeaconBlock(internalSignedBeaconBlock);
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

  @Test
  public void submitSignedBlock_shouldReturn500ForInternalError()
      throws ExecutionException, InterruptedException {
    final SignedBeaconBlock internalSignedBeaconBlock =
        dataStructureUtil.randomSignedBeaconBlock(1);
    final tech.pegasys.teku.api.schema.SignedBeaconBlock signedBeaconBlock =
        new tech.pegasys.teku.api.schema.SignedBeaconBlock(internalSignedBeaconBlock);

    final SafeFuture<SendSignedBlockResult> failImportResult =
        completedFuture(SendSignedBlockResult.rejected(FailureReason.INTERNAL_ERROR.name()));

    when(validatorApiChannel.sendSignedBlock(any())).thenReturn(failImportResult);

    final SafeFuture<ValidatorBlockResult> validatorBlockResultSafeFuture =
        provider.submitSignedBlock(signedBeaconBlock);

    assertThat(validatorBlockResultSafeFuture.get().getResponseCode()).isEqualTo(500);
  }

  @Test
  public void getAttesterDuties_shouldHandleEmptyIndexesList() {
    final SafeFuture<Optional<List<AttesterDuty>>> future =
        provider.getAttesterDuties(UInt64.ONE, List.of());
    assertThat(future).isCompleted();
    Optional<List<AttesterDuty>> maybeData = future.join();
    assertThat(maybeData.isPresent()).isTrue();
    assertThat(maybeData.get()).isEmpty();
  }

  @Test
  public void getAttesterDuties_shouldReturnDutiesForKnownValidator() {
    AttesterDuties v1 = new AttesterDuties(BLSPublicKey.random(0), 1, 2, 3, 4, ONE);
    AttesterDuties v2 = new AttesterDuties(BLSPublicKey.random(1), 11, 12, 13, 14, ZERO);
    when(validatorApiChannel.getAttestationDuties(eq(ONE), any()))
        .thenReturn(completedFuture(Optional.of(List.of(v1, v2))));

    final SafeFuture<Optional<List<AttesterDuty>>> future =
        provider.getAttesterDuties(ONE, List.of(1, 11));
    assertThat(future).isCompleted();
    final Optional<List<AttesterDuty>> maybeList = future.join();
    final List<AttesterDuty> list = maybeList.get();
    assertThat(list).containsExactlyInAnyOrder(asAttesterDuty(v1), asAttesterDuty(v2));
  }

  private AttesterDuty asAttesterDuty(final tech.pegasys.teku.validator.api.AttesterDuties duties) {

    return new AttesterDuty(
        new BLSPubKey(duties.getPublicKey()),
        UInt64.valueOf(duties.getValidatorIndex()),
        UInt64.valueOf(duties.getCommitteeIndex()),
        UInt64.valueOf(duties.getCommitteeLength()),
        UInt64.valueOf(duties.getValidatorCommitteeIndex()),
        duties.getSlot());
  }

  private List<BLSPubKey> generatePublicKeys(final int count) {
    return Stream.generate(dataStructureUtil::randomPublicKey)
        .map(BLSPublicKey::toBytesCompressed)
        .map(BLSPubKey::new)
        .limit(count)
        .collect(Collectors.toList());
  }
}
