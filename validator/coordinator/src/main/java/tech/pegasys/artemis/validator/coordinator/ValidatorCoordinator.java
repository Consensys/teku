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

import static tech.pegasys.artemis.datastructures.util.AttestationUtil.getGenericAttestationData;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_domain;
import static tech.pegasys.artemis.util.async.SafeFuture.reportExceptions;
import static tech.pegasys.artemis.util.config.Constants.DOMAIN_BEACON_ATTESTER;
import static tech.pegasys.artemis.util.config.Constants.GENESIS_EPOCH;
import static tech.pegasys.artemis.validator.coordinator.ValidatorCoordinatorUtil.isEpochStart;
import static tech.pegasys.artemis.validator.coordinator.ValidatorCoordinatorUtil.isGenesis;
import static tech.pegasys.artemis.validator.coordinator.ValidatorLoader.initializeValidators;
import static tech.pegasys.teku.logging.StatusLogger.STATUS_LOG;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.UnsignedLong;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockBodyLists;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.operations.AggregateAndProof;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.ProposerSlashing;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Committee;
import tech.pegasys.artemis.datastructures.state.MutableBeaconState;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.datastructures.util.AttestationUtil;
import tech.pegasys.artemis.datastructures.validator.AttesterInformation;
import tech.pegasys.artemis.datastructures.validator.MessageSignerService;
import tech.pegasys.artemis.service.serviceutils.Service;
import tech.pegasys.artemis.statetransition.AttestationAggregator;
import tech.pegasys.artemis.statetransition.BlockAttestationsPool;
import tech.pegasys.artemis.statetransition.BlockProposalUtil;
import tech.pegasys.artemis.statetransition.StateTransition;
import tech.pegasys.artemis.statetransition.StateTransitionException;
import tech.pegasys.artemis.statetransition.events.attestation.BroadcastAggregatesEvent;
import tech.pegasys.artemis.statetransition.events.attestation.BroadcastAttestationEvent;
import tech.pegasys.artemis.statetransition.events.attestation.ProcessedAggregateEvent;
import tech.pegasys.artemis.statetransition.events.attestation.ProcessedAttestationEvent;
import tech.pegasys.artemis.statetransition.events.block.ImportedBlockEvent;
import tech.pegasys.artemis.statetransition.events.block.ProposedBlockEvent;
import tech.pegasys.artemis.statetransition.util.EpochProcessingException;
import tech.pegasys.artemis.statetransition.util.SlotProcessingException;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.Store;
import tech.pegasys.artemis.storage.events.SlotEvent;
import tech.pegasys.artemis.util.SSZTypes.Bitlist;
import tech.pegasys.artemis.util.SSZTypes.SSZList;
import tech.pegasys.artemis.util.SSZTypes.SSZMutableList;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;
import tech.pegasys.artemis.util.time.TimeProvider;

/** This class coordinates validator(s) to act correctly in the beacon chain */
public class ValidatorCoordinator extends Service {

  private static final Logger LOG = LogManager.getLogger();

  private final EventBus eventBus;
  private final Map<BLSPublicKey, ValidatorInfo> validators;
  private final StateTransition stateTransition;
  private final BlockProposalUtil blockCreator;
  private final ChainStorageClient chainStorageClient;
  private final AttestationAggregator attestationAggregator;
  private final BlockAttestationsPool blockAttestationsPool;
  private final DepositProvider depositProvider;
  private Eth1DataCache eth1DataCache;
  private CommitteeAssignmentManager committeeAssignmentManager;

  //  maps slots to Lists of attestation informations
  //  (which contain information for our validators to produce attestations)
  private Map<UnsignedLong, List<AttesterInformation>> committeeAssignments = new HashMap<>();

  private LinkedBlockingQueue<ProposerSlashing> slashings = new LinkedBlockingQueue<>();

  public ValidatorCoordinator(
      TimeProvider timeProvider,
      EventBus eventBus,
      ChainStorageClient chainStorageClient,
      AttestationAggregator attestationAggregator,
      BlockAttestationsPool blockAttestationsPool,
      DepositProvider depositProvider,
      ArtemisConfiguration config) {
    this.eventBus = eventBus;
    this.chainStorageClient = chainStorageClient;
    this.stateTransition = new StateTransition();
    this.blockCreator = new BlockProposalUtil(stateTransition);
    this.validators = initializeValidators(config);
    this.attestationAggregator = attestationAggregator;
    this.blockAttestationsPool = blockAttestationsPool;
    this.depositProvider = depositProvider;
    this.eth1DataCache = new Eth1DataCache(eventBus, timeProvider);
    this.eventBus.register(this);
  }

  @Override
  protected SafeFuture<?> doStart() {
    chainStorageClient.subscribeBestBlockInitialized(this::onBestBlockInitialized);
    return SafeFuture.COMPLETE;
  }

  @Override
  protected SafeFuture<?> doStop() {
    return SafeFuture.COMPLETE;
  }

  private void onBestBlockInitialized() {
    final Store store = chainStorageClient.getStore();
    final Bytes32 head = chainStorageClient.getBestBlockRoot().orElseThrow();
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

  @Subscribe
  // TODO: make sure blocks that are produced right even after new slot to be pushed.
  public void onNewSlot(SlotEvent slotEvent) {
    UnsignedLong slot = slotEvent.getSlot();
    eth1DataCache.onSlot(slotEvent);

    final Optional<Bytes32> headRoot = chainStorageClient.getBestBlockRoot();
    if (!isGenesis(slot) && headRoot.isPresent()) {
      BeaconState headState = chainStorageClient.getStore().getBlockState(headRoot.get());
      BeaconBlock headBlock = chainStorageClient.getStore().getBlock(headRoot.get());
      createBlockIfNecessary(headState, headBlock, slot);
    }
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
      Store store = chainStorageClient.getStore();
      BeaconBlock headBlock = store.getBlock(event.getHeadBlockRoot());
      BeaconState headState = store.getBlockState(event.getHeadBlockRoot());
      UnsignedLong slot = event.getNodeSlot();

      if (!isGenesis(slot) && isEpochStart(slot)) {
        UnsignedLong epoch = compute_epoch_at_slot(slot);
        // NOTE: we get committee assignments for NEXT epoch
        reportExceptions(
            CompletableFuture.runAsync(
                () ->
                    committeeAssignmentManager.updateCommitteeAssignments(
                        headState, epoch.plus(UnsignedLong.ONE), eventBus)));
      }

      // Get attester information to prepare AttestationAggregator for new slot's aggregation
      List<AttesterInformation> attesterInformations = committeeAssignments.get(slot);

      // If our beacon node does have any attestation responsibilities for this slot
      if (attesterInformations == null) {
        return;
      }

      // Pass attestationAggregator all the attester information necessary
      // for aggregation
      attestationAggregator.updateAggregatorInformations(attesterInformations);

      asyncProduceAttestations(
          attesterInformations, headState, getGenericAttestationData(headState, headBlock));

      // Save headState to check for slashings
      //      this.headState = headState;
    } catch (IllegalArgumentException e) {
      STATUS_LOG.attestationFailure(e);
    }
  }

  @Subscribe
  public void onAggregationEvent(BroadcastAggregatesEvent event) {
    List<AggregateAndProof> aggregateAndProofs = attestationAggregator.getAggregateAndProofs();
    for (AggregateAndProof aggregateAndProof : aggregateAndProofs) {
      this.eventBus.post(aggregateAndProof);
    }
    attestationAggregator.reset();
  }

  private void produceAttestations(
      BeaconState state,
      BLSPublicKey attester,
      int indexIntoCommittee,
      Committee committee,
      AttestationData genericAttestationData) {
    int committeeSize = committee.getCommitteeSize();
    Bitlist aggregationBitfield =
        AttestationUtil.getAggregationBits(committeeSize, indexIntoCommittee);
    AttestationData attestationData = genericAttestationData.withIndex(committee.getIndex());
    Bytes32 attestationMessage = AttestationUtil.getAttestationMessageToSign(attestationData);
    Bytes domain =
        get_domain(state, DOMAIN_BEACON_ATTESTER, attestationData.getTarget().getEpoch());

    BLSSignature signature = validators.get(attester).sign(attestationMessage, domain).join();
    Attestation attestation = new Attestation(aggregationBitfield, attestationData, signature);
    attestationAggregator.addOwnValidatorAttestation(new Attestation(attestation));
    this.eventBus.post(attestation);
  }

  private void createBlockIfNecessary(
      BeaconState previousState, BeaconBlock previousBlock, UnsignedLong newSlot) {
    try {

      MutableBeaconState newState = previousState.createWritableCopy();
      // Process empty slots up to the new slot
      stateTransition.process_slots(newState, newSlot);

      // Check if we should be proposing
      final BLSPublicKey proposer = blockCreator.getProposerForSlot(newState, newSlot);
      if (!validators.containsKey(proposer)) {
        // We're not proposing now
        return;
      }

      SignedBeaconBlock newBlock;
      // Collect attestations to include
      SSZList<Attestation> attestations = blockAttestationsPool.getAttestationsForSlot(newSlot);
      // Collect slashing to include
      final SSZList<ProposerSlashing> slashingsInBlock = getSlashingsForBlock(newState);
      // Collect deposits
      final SSZList<Deposit> deposits = depositProvider.getDeposits(newState);

      final MessageSignerService signer = getSigner(proposer);
      Eth1Data eth1Data = eth1DataCache.get_eth1_vote(newState);
      final Bytes32 parentRoot = previousBlock.hash_tree_root();
      newBlock =
          blockCreator.createNewBlock(
              signer,
              newSlot,
              newState,
              parentRoot,
              eth1Data,
              attestations,
              slashingsInBlock,
              deposits);

      this.eventBus.post(new ProposedBlockEvent(newBlock));
      LOG.debug("Local validator produced a new block");
    } catch (SlotProcessingException | EpochProcessingException | StateTransitionException e) {
      STATUS_LOG.blockCreationFailure(e);
    }
  }

  private SSZList<ProposerSlashing> getSlashingsForBlock(final BeaconState state) {
    SSZMutableList<ProposerSlashing> slashingsForBlock =
        BeaconBlockBodyLists.createProposerSlashings();
    ProposerSlashing slashing = slashings.poll();
    while (slashing != null) {
      if (!state.getValidators().get(slashing.getProposer_index().intValue()).isSlashed()) {
        slashingsForBlock.add(slashing);
      }
      if (slashingsForBlock.size() >= slashingsForBlock.getMaxSize()) {
        break;
      }
      slashing = slashings.poll();
    }
    return slashingsForBlock;
  }

  private MessageSignerService getSigner(BLSPublicKey signer) {
    return validators.get(signer).getSignerService();
  }

  @VisibleForTesting
  void asyncProduceAttestations(
      List<AttesterInformation> attesterInformations,
      BeaconState state,
      AttestationData genericAttestationData) {
    reportExceptions(
        CompletableFuture.runAsync(
            () ->
                attesterInformations
                    .parallelStream()
                    .forEach(
                        attesterInfo ->
                            produceAttestations(
                                state,
                                attesterInfo.getPublicKey(),
                                attesterInfo.getIndexIntoCommitee(),
                                attesterInfo.getCommittee(),
                                genericAttestationData))));
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
