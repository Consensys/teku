/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.validator.coordinator;

import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.artemis.util.async.SafeFuture.reportExceptions;
import static tech.pegasys.artemis.util.config.Constants.GENESIS_EPOCH;
import static tech.pegasys.artemis.validator.client.loader.ValidatorLoader.initializeValidators;
import static tech.pegasys.artemis.validator.coordinator.ValidatorCoordinatorUtil.isEpochStart;
import static tech.pegasys.artemis.validator.coordinator.ValidatorCoordinatorUtil.isGenesis;
import static tech.pegasys.teku.logging.StatusLogger.STATUS_LOG;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.UnsignedLong;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.operations.AggregateAndProof;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.datastructures.validator.AttesterInformation;
import tech.pegasys.artemis.service.serviceutils.Service;
import tech.pegasys.artemis.statetransition.AttestationAggregator;
import tech.pegasys.artemis.statetransition.BlockAttestationsPool;
import tech.pegasys.artemis.statetransition.BlockProposalUtil;
import tech.pegasys.artemis.statetransition.StateTransition;
import tech.pegasys.artemis.statetransition.events.attestation.BroadcastAggregatesEvent;
import tech.pegasys.artemis.statetransition.events.attestation.BroadcastAttestationEvent;
import tech.pegasys.artemis.statetransition.events.attestation.ProcessedAggregateEvent;
import tech.pegasys.artemis.statetransition.events.attestation.ProcessedAttestationEvent;
import tech.pegasys.artemis.statetransition.events.block.ImportedBlockEvent;
import tech.pegasys.artemis.storage.Store;
import tech.pegasys.artemis.storage.client.RecentChainData;
import tech.pegasys.artemis.util.SSZTypes.Bitlist;
import tech.pegasys.artemis.util.SSZTypes.SSZList;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;
import tech.pegasys.artemis.util.config.FeatureToggles;
import tech.pegasys.artemis.util.time.channels.SlotEventsChannel;
import tech.pegasys.artemis.validator.api.ValidatorApiChannel;
import tech.pegasys.artemis.validator.client.signer.Signer;

/** This class coordinates validator(s) to act correctly in the beacon chain */
public class ValidatorCoordinator extends Service implements SlotEventsChannel {

  private static final Logger LOG = LogManager.getLogger();

  private final EventBus eventBus;
  private final Map<BLSPublicKey, ValidatorInfo> validators;
  private final StateTransition stateTransition;
  private final BlockProposalUtil blockCreator;
  private final RecentChainData recentChainData;
  private final AttestationAggregator attestationAggregator;
  private final BlockAttestationsPool blockAttestationsPool;
  private final ValidatorApiChannel validatorApiChannel;
  private Eth1DataCache eth1DataCache;
  private CommitteeAssignmentManager committeeAssignmentManager;

  //  maps slots to Lists of attestation information
  //  (which contain information for our validators to produce attestations)
  private Map<UnsignedLong, List<AttesterInformation>> committeeAssignments = new HashMap<>();

  public ValidatorCoordinator(
      EventBus eventBus,
      ValidatorApiChannel validatorApiChannel,
      RecentChainData recentChainData,
      AttestationAggregator attestationAggregator,
      BlockAttestationsPool blockAttestationsPool,
      Eth1DataCache eth1DataCache,
      ArtemisConfiguration config) {
    this.eventBus = eventBus;
    this.validatorApiChannel = validatorApiChannel;
    this.recentChainData = recentChainData;
    this.stateTransition = new StateTransition();
    this.blockCreator = new BlockProposalUtil(stateTransition);
    this.validators =
        initializeValidators(config).values().stream()
            .collect(
                Collectors.toMap(
                    tech.pegasys.artemis.validator.client.Validator::getPublicKey,
                    validator -> new ValidatorInfo(validator.getSigner())));
    this.attestationAggregator = attestationAggregator;
    this.blockAttestationsPool = blockAttestationsPool;
    this.eth1DataCache = eth1DataCache;
  }

  @Override
  protected SafeFuture<?> doStart() {
    this.eventBus.register(this);
    recentChainData.subscribeBestBlockInitialized(this::onBestBlockInitialized);
    return SafeFuture.COMPLETE;
  }

  @Override
  protected SafeFuture<?> doStop() {
    return SafeFuture.COMPLETE;
  }

  private void onBestBlockInitialized() {
    final Store store = recentChainData.getStore();
    final Bytes32 head = recentChainData.getBestBlockRoot().orElseThrow();
    final BeaconState headState = store.getBlockState(head);

    // Get validator indices of our own validators
    getIndicesOfOurValidators(headState, validators);

    this.committeeAssignmentManager =
        new CommitteeAssignmentManager(validators, committeeAssignments);
    eth1DataCache.startBeaconChainMode(headState);

    // Update committee assignments and subscribe to required committee indices for the next 2
    // epochs
    UnsignedLong genesisEpoch = UnsignedLong.valueOf(GENESIS_EPOCH);
    committeeAssignmentManager.updateCommitteeAssignments(headState, genesisEpoch, eventBus);
    committeeAssignmentManager.updateCommitteeAssignments(
        headState, genesisEpoch.plus(UnsignedLong.ONE), eventBus);
  }

  @Override
  public void onSlot(UnsignedLong slot) {
    if (!FeatureToggles.USE_VALIDATOR_CLIENT_SERVICE) {
      final Optional<Bytes32> headRoot = recentChainData.getBestBlockRoot();
      if (!isGenesis(slot) && headRoot.isPresent()) {
        BeaconState headState = recentChainData.getStore().getBlockState(headRoot.get());
        createBlockIfNecessary(headState, slot);
      }
    }

    eth1DataCache.onSlot(slot);
  }

  @Subscribe
  public void onProcessedAttestationEvent(ProcessedAttestationEvent event) {
    attestationAggregator.processAttestation(event.getAttestation());
  }

  @Subscribe
  public void onProcessedAggregateEvent(ProcessedAggregateEvent event) {
    blockAttestationsPool.addUnprocessedAggregateAttestationToQueue(event.getAttestation());
  }

  @Subscribe
  public void onBlockImported(ImportedBlockEvent event) {
    event
        .getBlock()
        .getMessage()
        .getBody()
        .getAttestations()
        .forEach(blockAttestationsPool::addAggregateAttestationProcessedInBlock);
  }

  @Subscribe
  public void onAttestationEvent(BroadcastAttestationEvent event) throws IllegalArgumentException {
    try {

      UnsignedLong slot = event.getNodeSlot();
      Store store = recentChainData.getStore();
      BeaconState headState = store.getBlockState(event.getHeadBlockRoot());

      if (!isGenesis(slot) && isEpochStart(slot)) {
        UnsignedLong epoch = compute_epoch_at_slot(slot);
        // NOTE: we get committee assignments for NEXT epoch
        reportExceptions(
            CompletableFuture.runAsync(
                () ->
                    committeeAssignmentManager.updateCommitteeAssignments(
                        headState, epoch.plus(UnsignedLong.ONE), eventBus)));
      }

      if (FeatureToggles.USE_VALIDATOR_CLIENT_SERVICE) {
        // Leave attestation creation to the validator client service.
        return;
      }

      // Get attester information to prepare AttestationAggregator for new slot's aggregation
      List<AttesterInformation> attestersInformation = committeeAssignments.get(slot);

      // If our beacon node does have any attestation responsibilities for this slot
      if (attestersInformation == null) {
        return;
      }

      // Pass attestationAggregator all the attester information necessary
      // for aggregation
      attestationAggregator.updateAggregatorInformations(attestersInformation);

      final Map<UnsignedLong, List<AttesterInformation>> attestersByCommittee =
          attestersInformation.stream()
              .collect(Collectors.groupingBy(info -> info.getCommittee().getIndex()));

      attestersByCommittee.forEach(
          (committeeIndex, attesters) ->
              validatorApiChannel
                  .createUnsignedAttestation(slot, committeeIndex.intValue())
                  .finish(
                      unsignedAttestationOptional ->
                          produceAttestations(
                              slot,
                              headState,
                              committeeIndex,
                              attesters,
                              unsignedAttestationOptional),
                      STATUS_LOG::attestationFailure));
    } catch (IllegalArgumentException e) {
      STATUS_LOG.attestationFailure(e);
    }
  }

  private void produceAttestations(
      final UnsignedLong slot,
      final BeaconState state,
      final UnsignedLong committeeIndex,
      final List<AttesterInformation> attesters,
      final Optional<Attestation> unsignedAttestationOptional) {
    unsignedAttestationOptional.ifPresentOrElse(
        unsignedAttestation -> produceAttestations(state, unsignedAttestation, attesters),
        () ->
            LOG.error(
                "No attestation produced for slot {} and committee {}", slot, committeeIndex));
  }

  private void produceAttestations(
      final BeaconState state,
      final Attestation unsignedAttestation,
      final List<AttesterInformation> attesters) {
    attesters.stream()
        .parallel()
        .forEach(
            attester ->
                createSignedAttestation(state, unsignedAttestation, attester)
                    .finish(
                        validatorApiChannel::sendSignedAttestation,
                        error ->
                            LOG.error(
                                "Failed to sign attestation for slot {} and validator {}",
                                state.getSlot(),
                                attester.getPublicKey())));
  }

  private SafeFuture<Attestation> createSignedAttestation(
      final BeaconState state,
      final Attestation unsignedAttestation,
      final AttesterInformation attester) {
    final Bitlist aggregationBitlist = new Bitlist(unsignedAttestation.getAggregation_bits());
    aggregationBitlist.setBit(attester.getIndexIntoCommittee());
    final AttestationData attestationData = unsignedAttestation.getData();
    return signAttestation(state, attester.getPublicKey(), attestationData)
        .thenApply(signature -> new Attestation(aggregationBitlist, attestationData, signature));
  }

  @Subscribe
  public void onAggregationEvent(BroadcastAggregatesEvent event) {
    List<AggregateAndProof> aggregateAndProofs = attestationAggregator.getAggregateAndProofs();
    for (AggregateAndProof aggregateAndProof : aggregateAndProofs) {
      this.eventBus.post(aggregateAndProof);
    }
    attestationAggregator.reset();
  }

  private SafeFuture<BLSSignature> signAttestation(
      final BeaconState state, final BLSPublicKey attester, final AttestationData attestationData) {
    return getSignerService(attester).signAttestationData(attestationData, state.getFork());
  }

  private void createBlockIfNecessary(BeaconState previousState, UnsignedLong newSlot) {
    try {

      // Process empty slots up to the new slot
      BeaconState newState = stateTransition.process_slots(previousState, newSlot);

      // Check if we should be proposing
      final BLSPublicKey proposer = blockCreator.getProposerForSlot(newState, newSlot);
      if (!validators.containsKey(proposer)) {
        // We're not proposing now
        return;
      }

      final Signer signer = getSignerService(proposer);
      final BLSSignature randaoReveal =
          signer.createRandaoReveal(compute_epoch_at_slot(newSlot), newState.getFork()).join();
      final BeaconBlock unsignedBlock =
          validatorApiChannel
              .createUnsignedBlock(newSlot, randaoReveal)
              .orTimeout(10, TimeUnit.SECONDS)
              .join()
              .orElseThrow(
                  () -> new NoSuchElementException("No block created for slot " + newSlot));

      final BLSSignature blockSignature =
          signer.signBlock(unsignedBlock, newState.getFork()).join();
      final SignedBeaconBlock newBlock = new SignedBeaconBlock(unsignedBlock, blockSignature);

      validatorApiChannel.sendSignedBlock(newBlock);
      LOG.debug("Local validator produced a new block");
    } catch (final Exception e) {
      STATUS_LOG.blockCreationFailure(e);
    }
  }

  private Signer getSignerService(BLSPublicKey signer) {
    return validators.get(signer).getSigner();
  }

  @VisibleForTesting
  static void getIndicesOfOurValidators(
      BeaconState state, Map<BLSPublicKey, ValidatorInfo> validators) {
    SSZList<Validator> validatorRegistry = state.getValidators();
    IntStream.range(0, validatorRegistry.size())
        .forEach(
            i -> {
              if (validators.containsKey(validatorRegistry.get(i).getPubkey())) {
                LOG.debug("owned index = {} : {}", i, validatorRegistry.get(i).getPubkey());
                validators.get(validatorRegistry.get(i).getPubkey()).setValidatorIndex(i);
              }
            });
  }
}
