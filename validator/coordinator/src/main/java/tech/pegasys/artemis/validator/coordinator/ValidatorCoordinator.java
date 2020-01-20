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
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.integer_squareroot;
import static tech.pegasys.artemis.util.alogger.ALogger.STDOUT;
import static tech.pegasys.artemis.util.async.SafeFuture.reportExceptions;
import static tech.pegasys.artemis.util.config.Constants.DOMAIN_BEACON_ATTESTER;
import static tech.pegasys.artemis.util.config.Constants.ETH1_FOLLOW_DISTANCE;
import static tech.pegasys.artemis.util.config.Constants.GENESIS_EPOCH;
import static tech.pegasys.artemis.util.config.Constants.MAX_DEPOSITS;
import static tech.pegasys.artemis.util.config.Constants.SLOTS_PER_ETH1_VOTING_PERIOD;
import static tech.pegasys.artemis.util.config.Constants.SLOTS_PER_HISTORICAL_ROOT;
import static tech.pegasys.artemis.validator.coordinator.ValidatorCoordinatorUtil.getSignature;
import static tech.pegasys.artemis.validator.coordinator.ValidatorCoordinatorUtil.isEpochStart;
import static tech.pegasys.artemis.validator.coordinator.ValidatorCoordinatorUtil.isGenesis;
import static tech.pegasys.artemis.validator.coordinator.ValidatorLoader.initializeValidators;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockBodyLists;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.datastructures.blocks.Eth1DataWithIndexAndDeposits;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.operations.AggregateAndProof;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.DepositWithIndex;
import tech.pegasys.artemis.datastructures.operations.ProposerSlashing;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.datastructures.state.Committee;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.datastructures.util.AttestationUtil;
import tech.pegasys.artemis.datastructures.util.DepositUtil;
import tech.pegasys.artemis.datastructures.validator.AttesterInformation;
import tech.pegasys.artemis.datastructures.validator.Signer;
import tech.pegasys.artemis.statetransition.AttestationAggregator;
import tech.pegasys.artemis.statetransition.BlockAttestationsPool;
import tech.pegasys.artemis.statetransition.BlockProposalUtil;
import tech.pegasys.artemis.statetransition.StateTransition;
import tech.pegasys.artemis.statetransition.StateTransitionException;
import tech.pegasys.artemis.statetransition.events.BlockImportedEvent;
import tech.pegasys.artemis.statetransition.events.BlockProposedEvent;
import tech.pegasys.artemis.statetransition.events.BroadcastAggregatesEvent;
import tech.pegasys.artemis.statetransition.events.BroadcastAttestationEvent;
import tech.pegasys.artemis.statetransition.events.ProcessedAggregateEvent;
import tech.pegasys.artemis.statetransition.events.ProcessedAttestationEvent;
import tech.pegasys.artemis.statetransition.util.EpochProcessingException;
import tech.pegasys.artemis.statetransition.util.SlotProcessingException;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.Store;
import tech.pegasys.artemis.storage.events.SlotEvent;
import tech.pegasys.artemis.storage.events.StoreInitializedEvent;
import tech.pegasys.artemis.util.SSZTypes.Bitlist;
import tech.pegasys.artemis.util.SSZTypes.SSZList;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;
import tech.pegasys.artemis.util.config.Constants;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;

/** This class coordinates the activity between the validator clients and the the beacon chain */
public class ValidatorCoordinator {
  private final EventBus eventBus;
  private final Map<BLSPublicKey, ValidatorInfo> validators;
  private final StateTransition stateTransition;
  private final BlockProposalUtil blockCreator;
  private final SSZList<Deposit> newDeposits = new SSZList<>(Deposit.class, MAX_DEPOSITS);
  private final ChainStorageClient chainStorageClient;
  private final AttestationAggregator attestationAggregator;
  private final BlockAttestationsPool blockAttestationsPool;
  private CommitteeAssignmentManager committeeAssignmentManager;

  //  maps slots to Lists of attestation informations
  //  (which contain information for our validators to produce attestations)
  private Map<UnsignedLong, List<AttesterInformation>> committeeAssignments = new HashMap<>();

  private LinkedBlockingQueue<ProposerSlashing> slashings = new LinkedBlockingQueue<>();
  private HashMap<UnsignedLong, Eth1DataWithIndexAndDeposits> eth1DataCache = new HashMap<>();

  public ValidatorCoordinator(
      EventBus eventBus,
      ChainStorageClient chainStorageClient,
      AttestationAggregator attestationAggregator,
      BlockAttestationsPool blockAttestationsPool,
      ArtemisConfiguration config) {
    this.eventBus = eventBus;
    this.chainStorageClient = chainStorageClient;
    this.stateTransition = new StateTransition(false);
    this.blockCreator = new BlockProposalUtil(stateTransition);
    this.validators = initializeValidators(config);
    this.attestationAggregator = attestationAggregator;
    this.blockAttestationsPool = blockAttestationsPool;
    this.eventBus.register(this);
  }
  /*
  @Subscribe
  public void checkIfIncomingBlockObeysSlashingConditions(BeaconBlock block) {

    int proposerIndex =
        BeaconStateUtil.get_beacon_proposer_index(headState);
    Validator proposer = headState.getValidator_registry().get(proposerIndex);

    checkArgument(
        bls_verify(
            proposer.getPubkey(),
            block.signing_root("signature"),
            block.getSignature(),
            get_domain(
                headState,
                Constants.DOMAIN_BEACON_PROPOSER,
                get_current_epoch(headState))),
        "Proposer signature is invalid");

    BeaconBlockHeader blockHeader =
        new BeaconBlockHeader(
            block.getSlot(),
            block.getParent_root(),
            block.getState_root(),
            block.getBody().hash_tree_root(),
            block.getSignature());
    UnsignedLong headerSlot = blockHeader.getSlot();
    if (store.getBeaconBlockHeaders(proposerIndex).isPresent()) {
      List<BeaconBlockHeader> headers = store.getBeaconBlockHeaders(proposerIndex).get();
      headers.forEach(
          (header) -> {
            if (header.getSlot().equals(headerSlot)
                && !header.hash_tree_root().equals(blockHeader.hash_tree_root())
                && !proposer.isSlashed()) {
              ProposerSlashing slashing =
                  new ProposerSlashing(UnsignedLong.valueOf(proposerIndex), blockHeader, header);
              slashings.add(slashing);
            }
          });
    }
    this.store.addUnprocessedBlockHeader(proposerIndex, blockHeader);
  }

  */

  @Subscribe
  public void onStoreInitializedEvent(final StoreInitializedEvent event) {
    // Any deposits pre-genesis can be ignored.
    newDeposits.clear();

    final Store store = chainStorageClient.getStore();
    final Bytes32 head = chainStorageClient.getBestBlockRoot();
    final BeaconState genesisState = store.getBlockState(head);

    // Get validator indices of our own validators
    getIndicesOfOurValidators(genesisState, validators);

    this.committeeAssignmentManager =
        new CommitteeAssignmentManager(validators, committeeAssignments);

    // Update committee assignments and subscribe to required committee indices for the next 2
    // epochs
    UnsignedLong genesisEpoch = UnsignedLong.valueOf(GENESIS_EPOCH);
    committeeAssignmentManager.updateCommitteeAssignments(genesisState, genesisEpoch, eventBus);
    committeeAssignmentManager.updateCommitteeAssignments(
        genesisState, genesisEpoch.plus(UnsignedLong.ONE), eventBus);
  }

  @Subscribe
  // TODO: make sure blocks that are produced right even after new slot to be pushed.
  public void onNewSlot(SlotEvent slotEvent) {
    UnsignedLong slot = slotEvent.getSlot();
    BeaconState headState =
        chainStorageClient.getStore().getBlockState(chainStorageClient.getBestBlockRoot());
    BeaconBlock headBlock =
        chainStorageClient.getStore().getBlock(chainStorageClient.getBestBlockRoot());

    // Copy state so that state transition during block creation
    // does not manipulate headState in storage
    if (!isGenesis(slot)) {
      createBlockIfNecessary(BeaconStateWithCache.fromBeaconState(headState), headBlock, slot);
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
  public void onBlockImported(BlockImportedEvent event) {
    event
        .getBlock()
        .getMessage()
        .getBody()
        .getAttestations()
        .forEach(blockAttestationsPool::addAggregateAttestationProcessedInBlock);
  }

  @Subscribe
  public void onNewDeposit(tech.pegasys.artemis.pow.event.Deposit event) {
    STDOUT.log(Level.DEBUG, "New deposit received by ValidatorCoordinator");
    Deposit deposit = DepositUtil.convertDepositEventToOperationDeposit(event);
    if (!newDeposits.contains(deposit)) newDeposits.add(deposit);
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
        // NOTE: we get commmittee assignments for NEXT epoch
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
      STDOUT.log(Level.WARN, "Can not produce attestations or create a block" + e.toString());
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
    int commmitteSize = committee.getCommitteeSize();
    Bitlist aggregationBitfield =
        AttestationUtil.getAggregationBits(commmitteSize, indexIntoCommittee);
    AttestationData attestationData = genericAttestationData.withIndex(committee.getIndex());
    Bytes32 attestationMessage = AttestationUtil.getAttestationMessageToSign(attestationData);
    Bytes domain =
        get_domain(state, DOMAIN_BEACON_ATTESTER, attestationData.getTarget().getEpoch());

    BLSSignature signature = getSignature(validators, attestationMessage, domain, attester);
    Attestation attestation = new Attestation(aggregationBitfield, attestationData, signature);
    attestationAggregator.addOwnValidatorAttestation(new Attestation(attestation));
    this.eventBus.post(attestation);
  }

  private void createBlockIfNecessary(
      BeaconStateWithCache previousState, BeaconBlock previousBlock, UnsignedLong newSlot) {
    try {

      BeaconStateWithCache newState = BeaconStateWithCache.deepCopy(previousState);
      // Process empty slots up to the new slot
      stateTransition.process_slots(newState, newSlot, false);

      // Check if we should be proposing
      final BLSPublicKey proposer = blockCreator.getProposerForSlot(newState, newSlot);
      if (!validators.containsKey(proposer)) {
        // We're not proposing now
        return;
      }

      SignedBeaconBlock newBlock;
      // Collect attestations to include
      SSZList<Attestation> attestations = getAttestationsForSlot(newSlot);
      // Collect slashing to include
      final SSZList<ProposerSlashing> slashingsInBlock = getSlashingsForBlock(newState);
      // Collect deposits
      final SSZList<Deposit> deposits = getDepositsForBlock();

      final Signer signer = getSigner(proposer);
      final Bytes32 parentRoot = previousBlock.hash_tree_root();
      newBlock =
          blockCreator.createNewBlock(
              signer, newSlot, newState, parentRoot, attestations, slashingsInBlock, deposits);

      this.eventBus.post(new BlockProposedEvent(newBlock));
      STDOUT.log(Level.DEBUG, "Local validator produced a new block");

      if (validators.get(proposer).isNaughty()) {
        final SignedBeaconBlock naughtyBlock =
            blockCreator.createEmptyBlock(signer, newSlot, newState, parentRoot);
        this.eventBus.post(naughtyBlock);
      }
    } catch (SlotProcessingException | EpochProcessingException | StateTransitionException e) {
      STDOUT.log(Level.ERROR, "Error during block creation " + e.toString());
    }
  }

  private SSZList<Attestation> getAttestationsForSlot(final UnsignedLong slot) {
    SSZList<Attestation> attestations = BeaconBlockBodyLists.createAttestations();
    if (slot.compareTo(
            UnsignedLong.valueOf(
                Constants.GENESIS_SLOT + Constants.MIN_ATTESTATION_INCLUSION_DELAY))
        >= 0) {

      UnsignedLong attestation_slot =
          slot.minus(UnsignedLong.valueOf(Constants.MIN_ATTESTATION_INCLUSION_DELAY));

      attestations =
          blockAttestationsPool.getAggregatedAttestationsForBlockAtSlot(attestation_slot);
    }
    return attestations;
  }

  private SSZList<ProposerSlashing> getSlashingsForBlock(final BeaconState state) {
    SSZList<ProposerSlashing> slashingsForBlock = BeaconBlockBodyLists.createProposerSlashings();
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

  private SSZList<Deposit> getDepositsForBlock() {
    // TODO - Look into how deposits should be managed, this seems wrong
    final SSZList<Deposit> deposits = BeaconBlockBodyLists.createDeposits();
    deposits.addAll(newDeposits);
    return deposits;
  }

  private Signer getSigner(BLSPublicKey signer) {
    return (message, domain) -> getSignature(validators, message, domain, signer);
  }

  public Eth1Data get_eth1_vote(BeaconState state, UnsignedLong previous_eth1_distance) {
    processEth1DataCache();
    List<Eth1Data> new_eth1_data =
        LongStream.range(
                ETH1_FOLLOW_DISTANCE.longValue(),
                ETH1_FOLLOW_DISTANCE.times(UnsignedLong.valueOf(2)).longValue())
            .mapToObj(value -> get_eth1_data(UnsignedLong.valueOf(value)))
            .collect(Collectors.toList());
    List<Eth1Data> all_eth1_data =
        LongStream.range(ETH1_FOLLOW_DISTANCE.longValue(), previous_eth1_distance.longValue())
            .mapToObj(value -> get_eth1_data(UnsignedLong.valueOf(value)))
            .collect(Collectors.toList());

    boolean period_tail =
        state
                .getSlot()
                .mod(UnsignedLong.valueOf(SLOTS_PER_ETH1_VOTING_PERIOD))
                .compareTo(integer_squareroot(UnsignedLong.valueOf(SLOTS_PER_HISTORICAL_ROOT)))
            >= 0;

    List<Eth1Data> votes_to_consider;
    if (period_tail) {
      votes_to_consider = all_eth1_data;
    } else {
      votes_to_consider = new_eth1_data;
    }

    List<Eth1Data> valid_votes = new ArrayList<>();

    for (Eth1Data vote : state.getEth1_data_votes()) {
      if (votes_to_consider.contains(vote)) {
        valid_votes.add(vote);
      }
    }

    return valid_votes.stream()
        .map(
            vote ->
                new MutablePair<>(
                    Collections.frequency(valid_votes, vote) - all_eth1_data.indexOf(vote), vote))
        .max(Comparator.comparing(MutablePair::getLeft))
        .map(Pair::getRight)
        .orElseGet(() -> get_eth1_data(ETH1_FOLLOW_DISTANCE));
  }

  @Subscribe
  public void updateEth1DataCache(tech.pegasys.artemis.pow.event.Deposit deposit) {
    UnsignedLong blockNumber = UnsignedLong.valueOf(deposit.getResponse().log.getBlockNumber());
    Bytes32 blockHash = Bytes32.fromHexString(deposit.getResponse().log.getBlockHash());

    // removes a deposit in the event of a reorg
    if (deposit.getResponse().log.isRemoved()) {
      List<DepositWithIndex> deposits =
          eth1DataCache.get(blockNumber).getDeposits().stream()
              .filter(
                  item ->
                      !item.getLog()
                          .getTransactionHash()
                          .equals(deposit.getResponse().log.getTransactionHash()))
              .collect(Collectors.toList());
      eth1DataCache.get(blockNumber).setDeposits(deposits);
    }

    DepositWithIndex depositWithIndex = DepositUtil.convertDepositEventToOperationDeposit(deposit);
    Eth1DataWithIndexAndDeposits eth1Data = eth1DataCache.get(blockNumber);
    if (eth1Data == null) {
      eth1Data = new Eth1DataWithIndexAndDeposits(blockNumber, new ArrayList<>(), blockHash);
      eth1DataCache.put(blockNumber, eth1Data);
    }
    if (!eth1Data.getBlock_hash().equals(blockHash)) {
      eth1Data.setBlock_hash(blockHash);
      eth1Data.setDeposits(new ArrayList<DepositWithIndex>());
    }
    eth1Data.getDeposits().add(depositWithIndex);
    eth1DataCache.put(blockNumber, eth1Data);
  }

  public void processEth1DataCache() {
    List<Eth1DataWithIndexAndDeposits> eth1DataWithIndexAndDeposits =
        eth1DataCache.values().stream().sorted().collect(Collectors.toList());

    List<DepositWithIndex> accumulatedDeposits = new ArrayList<>();
    for (final Eth1DataWithIndexAndDeposits item : eth1DataWithIndexAndDeposits) {
      accumulatedDeposits.addAll(item.getDeposits());
      Collections.sort(accumulatedDeposits);
      item.setDeposit_root(
          HashTreeUtil.hash_tree_root(
              HashTreeUtil.SSZTypes.LIST_OF_COMPOSITE,
              accumulatedDeposits.size(),
              accumulatedDeposits));
      item.setDeposit_count(UnsignedLong.valueOf(accumulatedDeposits.size()));
    }
    eth1DataCache.clear();
    eth1DataWithIndexAndDeposits.forEach(item -> eth1DataCache.put(item.getBlockNumber(), item));
  }

  public Eth1Data get_eth1_data(UnsignedLong distance) {
    UnsignedLong cacheSize =
        UnsignedLong.valueOf(
            eth1DataCache.entrySet().stream()
                .filter(item -> item.getValue().getDeposit_root() != null)
                .count());
    return eth1DataCache.get(cacheSize.minus(distance).minus(UnsignedLong.ONE));
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
    List<Validator> validatorRegistry = state.getValidators();
    IntStream.range(0, validatorRegistry.size())
        .forEach(
            i -> {
              if (validators.containsKey(validatorRegistry.get(i).getPubkey())) {
                STDOUT.log(
                    Level.DEBUG,
                    "owned index = " + i + ": " + validatorRegistry.get(i).getPubkey());
                validators.get(validatorRegistry.get(i).getPubkey()).setValidatorIndex(i);
              }
            });
  }
}
