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
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.api.response.v1.validator.PostAttesterDutiesResponse;
import tech.pegasys.teku.api.schema.Attestation;
import tech.pegasys.teku.api.schema.BLSPubKey;
import tech.pegasys.teku.api.schema.BLSSignature;
import tech.pegasys.teku.api.schema.BeaconBlock;
import tech.pegasys.teku.api.schema.ValidatorBlockResult;
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
import tech.pegasys.teku.validator.api.AttesterDuty;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;

public class ValidatorDataProviderTest {

  private final ArgumentCaptor<tech.pegasys.teku.datastructures.operations.Attestation> args =
      ArgumentCaptor.forClass(tech.pegasys.teku.datastructures.operations.Attestation.class);

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final CombinedChainDataClient combinedChainDataClient =
      mock(CombinedChainDataClient.class);
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private final ValidatorDataProvider provider =
      new ValidatorDataProvider(validatorApiChannel, combinedChainDataClient);
  private final tech.pegasys.teku.datastructures.blocks.BeaconBlock blockInternal =
      dataStructureUtil.randomBeaconBlock(123);
  private final BeaconBlock block = new BeaconBlock(blockInternal);
  private final tech.pegasys.teku.bls.BLSSignature signatureInternal =
      tech.pegasys.teku.bls.BLSSignature.random(1234);
  private final BLSSignature signature = new BLSSignature(signatureInternal);

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
  void getUnsignedAttestationAtSlot_shouldReturnAttestation() {
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

  @Test
  public void getAttesterDuties_shouldReturnDutiesForKnownValidator() {
    AttesterDuty v1 = new AttesterDuty(BLSPublicKey.random(0), 1, 2, 3, 15, 4, ONE);
    AttesterDuty v2 = new AttesterDuty(BLSPublicKey.random(1), 11, 12, 13, 15, 14, ZERO);
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
