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

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.request.v1.validator.BeaconCommitteeSubscriptionRequest;
import tech.pegasys.teku.api.response.v1.validator.GetProposerDutiesResponse;
import tech.pegasys.teku.api.response.v1.validator.PostAttesterDutiesResponse;
import tech.pegasys.teku.api.response.v1.validator.PostSyncDutiesResponse;
import tech.pegasys.teku.api.schema.Attestation;
import tech.pegasys.teku.api.schema.AttestationData;
import tech.pegasys.teku.api.schema.BLSPubKey;
import tech.pegasys.teku.api.schema.BLSSignature;
import tech.pegasys.teku.api.schema.BeaconBlock;
import tech.pegasys.teku.api.schema.SignedAggregateAndProof;
import tech.pegasys.teku.api.schema.SignedBeaconBlock;
import tech.pegasys.teku.api.schema.ValidatorBlockResult;
import tech.pegasys.teku.api.schema.altair.SyncCommitteeSignature;
import tech.pegasys.teku.api.schema.altair.SyncCommitteeSubnetSubscription;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult.FailureReason;
import tech.pegasys.teku.storage.client.ChainDataUnavailableException;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.validator.api.AttesterDuty;
import tech.pegasys.teku.validator.api.CommitteeSubscriptionRequest;
import tech.pegasys.teku.validator.api.ProposerDuty;
import tech.pegasys.teku.validator.api.SubmitCommitteeSignaturesResult;
import tech.pegasys.teku.validator.api.SyncCommitteeDuty;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;

public class ValidatorDataProvider {

  public static final String CANNOT_PRODUCE_HISTORIC_BLOCK =
      "Cannot produce a block for a historic slot.";
  public static final String NO_SLOT_PROVIDED = "No slot was provided.";
  public static final String NO_RANDAO_PROVIDED = "No randao_reveal was provided.";
  private final ValidatorApiChannel validatorApiChannel;
  private final CombinedChainDataClient combinedChainDataClient;

  private static final int SC_INTERNAL_ERROR = 500;
  private static final int SC_ACCEPTED = 202;
  private static final int SC_OK = 200;
  private final Spec spec;

  public ValidatorDataProvider(
      final Spec spec,
      final ValidatorApiChannel validatorApiChannel,
      final CombinedChainDataClient combinedChainDataClient) {
    this.validatorApiChannel = validatorApiChannel;
    this.combinedChainDataClient = combinedChainDataClient;
    this.spec = spec;
  }

  public boolean isStoreAvailable() {
    return combinedChainDataClient.isStoreAvailable();
  }

  public SafeFuture<Optional<BeaconBlock>> getUnsignedBeaconBlockAtSlot(
      UInt64 slot, BLSSignature randao, Optional<Bytes32> graffiti) {
    if (slot == null) {
      throw new IllegalArgumentException(NO_SLOT_PROVIDED);
    }
    if (randao == null) {
      throw new IllegalArgumentException(NO_RANDAO_PROVIDED);
    }
    final int slotsPerEpoch = spec.atSlot(slot).getConfig().getSlotsPerEpoch();
    final UInt64 currentSlot = combinedChainDataClient.getCurrentSlot();
    if (currentSlot.plus(slotsPerEpoch).isLessThan(slot)) {
      throw new IllegalArgumentException(
          "Cannot produce a block more than " + slotsPerEpoch + " slots in the future.");
    }
    if (currentSlot.isGreaterThan(slot)) {
      throw new IllegalArgumentException(CANNOT_PRODUCE_HISTORIC_BLOCK);
    }

    return validatorApiChannel
        .createUnsignedBlock(
            slot,
            tech.pegasys.teku.bls.BLSSignature.fromBytesCompressed(randao.getBytes()),
            graffiti)
        .thenApply(maybeBlock -> maybeBlock.map(BeaconBlock::new));
  }

  public SafeFuture<Optional<Attestation>> createUnsignedAttestationAtSlot(
      UInt64 slot, int committeeIndex) {
    if (!isStoreAvailable()) {
      return SafeFuture.failedFuture(new ChainDataUnavailableException());
    }
    return validatorApiChannel
        .createUnsignedAttestation(slot, committeeIndex)
        .thenApply(
            maybeAttestation ->
                maybeAttestation.map(
                    attestation ->
                        new Attestation(
                            attestation.getAggregation_bits(),
                            new AttestationData(attestation.getData()),
                            new BLSSignature(attestation.getAggregate_signature()))));
  }

  public void submitAttestations(List<Attestation> attestations) {
    attestations.forEach(this::submitAttestation);
  }

  public void submitAttestation(Attestation attestation) {
    if (attestation.signature.asInternalBLSSignature().toSSZBytes().isZero()) {
      throw new IllegalArgumentException("Signed attestations must have a non zero signature");
    }
    validatorApiChannel.sendSignedAttestation(attestation.asInternalAttestation());
  }

  public SafeFuture<ValidatorBlockResult> submitSignedBlock(
      final SignedBeaconBlock signedBeaconBlock) {
    return validatorApiChannel
        .sendSignedBlock(signedBeaconBlock.asInternalSignedBeaconBlock(spec))
        .thenApply(
            result -> {
              int responseCode;
              Optional<Bytes32> hashRoot = result.getBlockRoot();
              if (result.getRejectionReason().isEmpty()) {
                responseCode = SC_OK;
              } else if (result
                  .getRejectionReason()
                  .get()
                  .equals(FailureReason.INTERNAL_ERROR.name())) {
                responseCode = SC_INTERNAL_ERROR;
              } else {
                responseCode = SC_ACCEPTED;
              }

              return new ValidatorBlockResult(responseCode, result.getRejectionReason(), hashRoot);
            });
  }

  public SafeFuture<Optional<SubmitCommitteeSignaturesResult>> submitCommitteeSignatures(
      final List<SyncCommitteeSignature> signatures) {
    return validatorApiChannel
        .sendSyncCommitteeSignatures(
            signatures.stream()
                .flatMap(signature -> signature.asInternalCommitteeSignature(spec).stream())
                .collect(Collectors.toList()))
        .thenApply(
            errors ->
                errors.isEmpty()
                    ? Optional.empty()
                    : Optional.of(new SubmitCommitteeSignaturesResult(errors)));
  }

  public SafeFuture<Optional<Attestation>> createAggregate(
      final UInt64 slot, final Bytes32 attestationHashTreeRoot) {
    return validatorApiChannel
        .createAggregate(slot, attestationHashTreeRoot)
        .thenApply(maybeAttestation -> maybeAttestation.map(Attestation::new));
  }

  public void sendAggregateAndProofs(List<SignedAggregateAndProof> aggregateAndProofs) {
    aggregateAndProofs.stream()
        .map(SignedAggregateAndProof::asInternalSignedAggregateAndProof)
        .forEach(validatorApiChannel::sendAggregateAndProof);
  }

  public void subscribeToBeaconCommittee(final List<BeaconCommitteeSubscriptionRequest> requests) {
    validatorApiChannel.subscribeToBeaconCommittee(
        requests.stream()
            .map(
                request ->
                    new CommitteeSubscriptionRequest(
                        request.validator_index,
                        request.committee_index,
                        request.committees_at_slot,
                        request.slot,
                        request.is_aggregator))
            .collect(toList()));
  }

  public void subscribeToSyncCommitteeSubnets(
      final List<SyncCommitteeSubnetSubscription> subscriptions) {
    validatorApiChannel.subscribeToSyncCommitteeSubnets(
        subscriptions.stream()
            .map(
                subscription ->
                    new tech.pegasys.teku.validator.api.SyncCommitteeSubnetSubscription(
                        subscription.validatorIndex.intValue(),
                        subscription.syncCommitteeIndices.stream()
                            .map(UInt64::intValue)
                            .collect(toList()),
                        subscription.untilEpoch))
            .collect(toList()));
  }

  public SafeFuture<Optional<PostAttesterDutiesResponse>> getAttesterDuties(
      final UInt64 epoch, final List<Integer> indexes) {
    return SafeFuture.of(() -> validatorApiChannel.getAttestationDuties(epoch, indexes))
        .thenApply(
            res ->
                res.map(
                    duties ->
                        new PostAttesterDutiesResponse(
                            duties.getDependentRoot(),
                            duties.getDuties().stream()
                                .filter(duty -> duty.getPublicKey() != null)
                                .map(this::mapToAttesterDuties)
                                .collect(toList()))));
  }

  public SafeFuture<Optional<GetProposerDutiesResponse>> getProposerDuties(final UInt64 epoch) {
    return SafeFuture.of(() -> validatorApiChannel.getProposerDuties(epoch))
        .thenApply(
            res ->
                res.map(
                    duties ->
                        new GetProposerDutiesResponse(
                            duties.getDependentRoot(),
                            duties.getDuties().stream()
                                .filter(duty -> duty.getPublicKey() != null)
                                .map(this::mapToProposerDuties)
                                .collect(toList()))));
  }

  private tech.pegasys.teku.api.response.v1.validator.ProposerDuty mapToProposerDuties(
      final ProposerDuty duties) {
    return new tech.pegasys.teku.api.response.v1.validator.ProposerDuty(
        new BLSPubKey(duties.getPublicKey()), duties.getValidatorIndex(), duties.getSlot());
  }

  private tech.pegasys.teku.api.response.v1.validator.AttesterDuty mapToAttesterDuties(
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

  public SafeFuture<Optional<PostSyncDutiesResponse>> getSyncDuties(
      final UInt64 epoch, final List<Integer> indexes) {
    return SafeFuture.of(() -> validatorApiChannel.getSyncCommitteeDuties(epoch, indexes))
        .thenApply(
            res ->
                res.map(
                    duties ->
                        new PostSyncDutiesResponse(
                            duties.getDuties().stream()
                                .filter(duty -> duty.getPublicKey() != null)
                                .map(this::mapToSyncCommitteeDuty)
                                .collect(toList()))));
  }

  private tech.pegasys.teku.api.response.v1.validator.SyncCommitteeDuty mapToSyncCommitteeDuty(
      final SyncCommitteeDuty duty) {
    return new tech.pegasys.teku.api.response.v1.validator.SyncCommitteeDuty(
        new BLSPubKey(duty.getPublicKey().toBytesCompressed()),
        UInt64.valueOf(duty.getValidatorIndex()),
        duty.getSyncCommitteeIndices());
  }
}
