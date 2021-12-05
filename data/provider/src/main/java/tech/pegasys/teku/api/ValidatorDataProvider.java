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

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.api.request.v1.validator.BeaconCommitteeSubscriptionRequest;
import tech.pegasys.teku.api.response.v1.beacon.PostDataFailure;
import tech.pegasys.teku.api.response.v1.beacon.PostDataFailureResponse;
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
import tech.pegasys.teku.api.schema.altair.SignedBeaconBlockAltair;
import tech.pegasys.teku.api.schema.altair.SignedContributionAndProof;
import tech.pegasys.teku.api.schema.altair.SyncCommitteeMessage;
import tech.pegasys.teku.api.schema.altair.SyncCommitteeSubnetSubscription;
import tech.pegasys.teku.api.schema.merge.BeaconPreparableProposer;
import tech.pegasys.teku.api.schema.merge.SignedBeaconBlockMerge;
import tech.pegasys.teku.api.schema.phase0.SignedBeaconBlockPhase0;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.http.HttpStatusCodes;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeContribution;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeContributionSchema;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult.FailureReason;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;
import tech.pegasys.teku.storage.client.ChainDataUnavailableException;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.validator.api.AttesterDuty;
import tech.pegasys.teku.validator.api.CommitteeSubscriptionRequest;
import tech.pegasys.teku.validator.api.ProposerDuty;
import tech.pegasys.teku.validator.api.SubmitDataError;
import tech.pegasys.teku.validator.api.SyncCommitteeDuty;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;

public class ValidatorDataProvider {

  public static final String CANNOT_PRODUCE_HISTORIC_BLOCK =
      "Cannot produce a block for a historic slot.";
  public static final String NO_SLOT_PROVIDED = "No slot was provided.";
  public static final String NO_RANDAO_PROVIDED = "No randao_reveal was provided.";
  static final String PARTIAL_PUBLISH_FAILURE_MESSAGE =
      "Some items failed to publish, refer to errors for details";
  private final ValidatorApiChannel validatorApiChannel;
  private final CombinedChainDataClient combinedChainDataClient;
  private final SchemaObjectProvider schemaObjectProvider;

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
    this.schemaObjectProvider = new SchemaObjectProvider(spec);
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
        .thenApply(maybeBlock -> maybeBlock.map(schemaObjectProvider::getBeaconBlock));
  }

  public SpecMilestone getMilestoneAtSlot(final UInt64 slot) {
    return spec.atSlot(slot).getMilestone();
  }

  public SafeFuture<Optional<AttestationData>> createAttestationDataAtSlot(
      UInt64 slot, int committeeIndex) {
    if (!isStoreAvailable()) {
      return SafeFuture.failedFuture(new ChainDataUnavailableException());
    }
    return validatorApiChannel
        .createAttestationData(slot, committeeIndex)
        .thenApply(maybeAttestation -> maybeAttestation.map(AttestationData::new))
        .exceptionallyCompose(
            error -> {
              final Throwable rootCause = Throwables.getRootCause(error);
              if (rootCause instanceof IllegalArgumentException) {
                return SafeFuture.failedFuture(new BadRequestException(rootCause.getMessage()));
              }
              return SafeFuture.failedFuture(error);
            });
  }

  public SafeFuture<Optional<PostDataFailureResponse>> submitAttestations(
      List<Attestation> attestations) {
    return validatorApiChannel
        .sendSignedAttestations(
            attestations.stream().map(Attestation::asInternalAttestation).collect(toList()))
        .thenApply(this::convertToPostDataFailureResponse);
  }

  public SignedBeaconBlock parseBlock(final JsonProvider jsonProvider, final String jsonBlock)
      throws JsonProcessingException {
    final ObjectMapper mapper = jsonProvider.getObjectMapper();
    final JsonNode jsonNode = mapper.readTree(jsonBlock);
    final UInt64 slot = mapper.treeToValue(jsonNode.findValue("slot"), UInt64.class);
    final SignedBeaconBlock signedBeaconBlock;
    checkNotNull(slot, "Slot was not found in json block");
    switch (spec.atSlot(slot).getMilestone()) {
      case PHASE0:
        signedBeaconBlock = mapper.treeToValue(jsonNode, SignedBeaconBlockPhase0.class);
        break;
      case ALTAIR:
        signedBeaconBlock = mapper.treeToValue(jsonNode, SignedBeaconBlockAltair.class);
        break;
      case MERGE:
        signedBeaconBlock = mapper.treeToValue(jsonNode, SignedBeaconBlockMerge.class);
        break;
      default:
        throw new IllegalArgumentException("Could not determine milestone for slot " + slot);
    }
    return signedBeaconBlock;
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

  public SafeFuture<Optional<PostDataFailureResponse>> submitCommitteeSignatures(
      final List<SyncCommitteeMessage> messages) {
    return validatorApiChannel
        .sendSyncCommitteeMessages(
            messages.stream()
                .flatMap(message -> message.asInternalCommitteeSignature(spec).stream())
                .collect(Collectors.toList()))
        .thenApply(this::convertToPostDataFailureResponse);
  }

  private Optional<PostDataFailureResponse> convertToPostDataFailureResponse(
      final List<SubmitDataError> errors) {
    if (errors.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        new PostDataFailureResponse(
            HttpStatusCodes.SC_BAD_REQUEST,
            PARTIAL_PUBLISH_FAILURE_MESSAGE,
            errors.stream()
                .map(e -> new PostDataFailure(e.getIndex(), e.getMessage()))
                .collect(Collectors.toList())));
  }

  public SafeFuture<Optional<Attestation>> createAggregate(
      final UInt64 slot, final Bytes32 attestationHashTreeRoot) {
    return validatorApiChannel
        .createAggregate(slot, attestationHashTreeRoot)
        .thenApply(maybeAttestation -> maybeAttestation.map(Attestation::new));
  }

  public SafeFuture<Optional<PostDataFailureResponse>> sendAggregateAndProofs(
      List<SignedAggregateAndProof> aggregateAndProofs) {
    return validatorApiChannel
        .sendAggregateAndProofs(
            aggregateAndProofs.stream()
                .map(SignedAggregateAndProof::asInternalSignedAggregateAndProof)
                .collect(toList()))
        .thenApply(this::convertToPostDataFailureResponse);
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
      final Collection<SyncCommitteeSubnetSubscription> subscriptions) {
    validatorApiChannel.subscribeToSyncCommitteeSubnets(
        subscriptions.stream()
            .map(
                subscription ->
                    new tech.pegasys.teku.validator.api.SyncCommitteeSubnetSubscription(
                        subscription.validatorIndex.intValue(),
                        subscription.syncCommitteeIndices.stream()
                            .map(UInt64::intValue)
                            .collect(toSet()),
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

  public SafeFuture<Optional<tech.pegasys.teku.api.schema.altair.SyncCommitteeContribution>>
      createSyncCommitteeContribution(
          final UInt64 slot, final int subcommitteeIndex, final Bytes32 blockRoot) {
    return validatorApiChannel
        .createSyncCommitteeContribution(slot, subcommitteeIndex, blockRoot)
        .thenApply(
            maybeContribution -> maybeContribution.map(this::toSchemaSyncCommitteeContribution));
  }

  private tech.pegasys.teku.api.schema.altair.SyncCommitteeContribution
      toSchemaSyncCommitteeContribution(final SyncCommitteeContribution contribution) {
    return new tech.pegasys.teku.api.schema.altair.SyncCommitteeContribution(
        contribution.getSlot(),
        contribution.getBeaconBlockRoot(),
        contribution.getSubcommitteeIndex(),
        contribution.getAggregationBits().sszSerialize(),
        new BLSSignature(contribution.getSignature()));
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
        duty.getValidatorSyncCommitteeIndices());
  }

  public SafeFuture<Void> sendContributionAndProofs(
      final List<SignedContributionAndProof> contributionAndProofs) {
    return validatorApiChannel.sendSignedContributionAndProofs(
        contributionAndProofs.stream()
            .map(this::asInternalContributionAndProofs)
            .collect(toList()));
  }

  public void prepareBeaconProposer(
      Collection<BeaconPreparableProposer> beaconPreparableProposers) {
    List<tech.pegasys.teku.spec.datastructures.operations.versions.merge.BeaconPreparableProposer>
        internalBeaconPreparableProposer =
            beaconPreparableProposers.stream()
                .map(BeaconPreparableProposer::asInternalBeaconPreparableProposer)
                .collect(Collectors.toUnmodifiableList());

    validatorApiChannel.prepareBeaconProposer(internalBeaconPreparableProposer);
  }

  public boolean isPhase0Slot(final UInt64 slot) {
    return spec.atSlot(slot).getMilestone() == SpecMilestone.PHASE0;
  }

  private tech.pegasys.teku.spec.datastructures.operations.versions.altair
          .SignedContributionAndProof
      asInternalContributionAndProofs(final SignedContributionAndProof signedContributionAndProof) {
    final UInt64 slot = signedContributionAndProof.message.contribution.slot;
    final Bytes32 root = signedContributionAndProof.message.contribution.beaconBlockRoot;
    final UInt64 subcommitteeIndex =
        signedContributionAndProof.message.contribution.subcommitteeIndex;
    final Iterable<Integer> indices =
        getAggregationBits(signedContributionAndProof.message.contribution.aggregationBits, slot);

    final tech.pegasys.teku.bls.BLSSignature signature =
        signedContributionAndProof.message.contribution.signature.asInternalBLSSignature();
    final SyncCommitteeContribution contribution =
        spec.getSyncCommitteeUtilRequired(slot)
            .createSyncCommitteeContribution(slot, root, subcommitteeIndex, indices, signature);

    final ContributionAndProof message =
        spec.getSyncCommitteeUtilRequired(slot)
            .createContributionAndProof(
                signedContributionAndProof.message.aggregatorIndex,
                contribution,
                signedContributionAndProof.message.selectionProof.asInternalBLSSignature());

    return spec.getSyncCommitteeUtilRequired(slot)
        .createSignedContributionAndProof(
            message, signedContributionAndProof.signature.asInternalBLSSignature());
  }

  private Iterable<Integer> getAggregationBits(final Bytes aggregationBits, final UInt64 slot) {
    final SchemaDefinitionsAltair altairDefinitions =
        SchemaDefinitionsAltair.required(spec.atSlot(slot).getSchemaDefinitions());
    final SyncCommitteeContributionSchema syncCommitteeContributionSchema =
        altairDefinitions.getSyncCommitteeContributionSchema();
    final SszBitvector aggregationBitsVector =
        syncCommitteeContributionSchema.getAggregationBitsSchema().fromBytes(aggregationBits);

    return aggregationBitsVector.getAllSetBits();
  }
}
