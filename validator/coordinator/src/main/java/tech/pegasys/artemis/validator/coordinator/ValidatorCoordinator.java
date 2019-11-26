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
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.bytes_to_int;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_domain;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.integer_squareroot;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.max;
import static tech.pegasys.artemis.datastructures.util.CommitteeUtil.get_beacon_committee;
import static tech.pegasys.artemis.util.alogger.ALogger.STDOUT;
import static tech.pegasys.artemis.util.config.Constants.DOMAIN_BEACON_ATTESTER;
import static tech.pegasys.artemis.util.config.Constants.ETH1_FOLLOW_DISTANCE;
import static tech.pegasys.artemis.util.config.Constants.GENESIS_SLOT;
import static tech.pegasys.artemis.util.config.Constants.MAX_ATTESTATIONS;
import static tech.pegasys.artemis.util.config.Constants.MAX_DEPOSITS;
import static tech.pegasys.artemis.util.config.Constants.SLOTS_PER_EPOCH;
import static tech.pegasys.artemis.util.config.Constants.SLOTS_PER_ETH1_VOTING_PERIOD;
import static tech.pegasys.artemis.util.config.Constants.SLOTS_PER_HISTORICAL_ROOT;
import static tech.pegasys.artemis.util.config.Constants.TARGET_AGGREGATORS_PER_COMMITTEE;
import static tech.pegasys.artemis.validator.coordinator.ValidatorLoader.initializeValidators;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.UnsignedLong;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.Hash;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.datastructures.blocks.Eth1DataWithIndexAndDeposits;
import tech.pegasys.artemis.datastructures.operations.AggregateAndProof;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.DepositWithIndex;
import tech.pegasys.artemis.datastructures.operations.ProposerSlashing;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.datastructures.state.Committee;
import tech.pegasys.artemis.datastructures.util.AttestationUtil;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.datastructures.util.DepositUtil;
import tech.pegasys.artemis.datastructures.validator.AttesterInformation;
import tech.pegasys.artemis.proto.messagesigner.MessageSignerGrpc;
import tech.pegasys.artemis.proto.messagesigner.SignatureRequest;
import tech.pegasys.artemis.proto.messagesigner.SignatureResponse;
import tech.pegasys.artemis.statetransition.AttestationAggregator;
import tech.pegasys.artemis.statetransition.BlockAttestationsPool;
import tech.pegasys.artemis.statetransition.CommitteeAssignment;
import tech.pegasys.artemis.statetransition.StateTransition;
import tech.pegasys.artemis.statetransition.StateTransitionException;
import tech.pegasys.artemis.statetransition.events.BroadcastAggregatesEvent;
import tech.pegasys.artemis.statetransition.events.BroadcastAttestationEvent;
import tech.pegasys.artemis.statetransition.events.ProcessedAggregateEvent;
import tech.pegasys.artemis.statetransition.events.ProcessedAttestationEvent;
import tech.pegasys.artemis.statetransition.events.ProcessedBlockEvent;
import tech.pegasys.artemis.statetransition.util.CommitteeAssignmentUtil;
import tech.pegasys.artemis.statetransition.util.EpochProcessingException;
import tech.pegasys.artemis.statetransition.util.SlotProcessingException;
import tech.pegasys.artemis.statetransition.util.StartupUtil;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.Store;
import tech.pegasys.artemis.storage.events.SlotEvent;
import tech.pegasys.artemis.util.SSZTypes.Bitlist;
import tech.pegasys.artemis.util.SSZTypes.SSZList;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;
import tech.pegasys.artemis.util.config.Constants;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil.SSZTypes;

/** This class coordinates the activity between the validator clients and the the beacon chain */
public class ValidatorCoordinator {
  private final EventBus eventBus;
  private final Map<BLSPublicKey, ValidatorInfo> validators;
  private StateTransition stateTransition;
  private SSZList<Deposit> newDeposits = new SSZList<>(Deposit.class, MAX_DEPOSITS);
  private ChainStorageClient chainStorageClient;
  private AttestationAggregator attestationAggregator;
  private BlockAttestationsPool blockAttestationsPool;

  //  maps slots to Lists of attestation informations
  //  (which contain information for our validators to produce attestations)
  private Map<UnsignedLong, List<AttesterInformation>> attestationAssignments = new HashMap<>();

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
    this.validators = initializeValidators(config, chainStorageClient);
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
      createBlockIfNecessary(BeaconStateWithCache.fromBeaconState(headState), headBlock);
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
  public void onProcessedBlockEvent(ProcessedBlockEvent event) {
    event
        .getAttestationList()
        .forEach(
            attestation ->
                blockAttestationsPool.addAggregateAttestationProcessedInBlock(attestation));
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
      UnsignedLong slot = headState.getSlot();

      // At the start of each epoch or at genesis, update attestation assignments
      // for all validators
      if (isGenesisOrEpochStart(slot)) {
        updateAttestationAssignments(headState);
      }

      // Get attester information to prepare AttestationAggregator for new slot's aggregation
      List<AttesterInformation> attesterInformations = attestationAssignments.get(slot);

      // Reset the attestation validator and pass attester information necessary
      // for validator to know which committees and validators to aggregate for
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

    BLSSignature signature = getSignature(attestationMessage, domain, attester);
    Attestation attestation = new Attestation(aggregationBitfield, attestationData, signature);
    attestationAggregator.addOwnValidatorAttestation(new Attestation(attestation));
    this.eventBus.post(attestation);
  }

  /**
   * Gets the epoch signature used for RANDAO from the Validator Client using gRPC
   *
   * @param state
   * @param proposer
   * @return
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.1/specs/validator/0_beacon-chain-validator.md#randao-reveal</a>
   */
  // TODO: since this is very similar to a spec function now, move it to a util file and
  // abstract away the gRPC details
  public BLSSignature get_epoch_signature(BeaconState state, BLSPublicKey proposer) {
    UnsignedLong slot = state.getSlot().plus(UnsignedLong.ONE);
    UnsignedLong epoch = BeaconStateUtil.compute_epoch_at_slot(slot);

    Bytes32 messageHash =
        HashTreeUtil.hash_tree_root(SSZTypes.BASIC, SSZ.encodeUInt64(epoch.longValue()));
    Bytes domain = get_domain(state, Constants.DOMAIN_RANDAO, epoch);
    return getSignature(messageHash, domain, proposer);
  }

  /**
   * Gets the block signature from the Validator Client using gRPC
   *
   * @param state
   * @param block
   * @param proposer
   * @return
   * @see
   *     <a>https://github.com/ethereum/eth2.0-specs/blob/v0.8.1/specs/validator/0_beacon-chain-validator.md#signature</a>
   */
  private BLSSignature getBlockSignature(
      BeaconState state, BeaconBlock block, BLSPublicKey proposer) {
    Bytes domain =
        get_domain(
            state,
            Constants.DOMAIN_BEACON_PROPOSER,
            BeaconStateUtil.compute_epoch_at_slot(block.getSlot()));

    Bytes32 blockRoot = block.signing_root("signature");

    return getSignature(blockRoot, domain, proposer);
  }

  private BeaconBlock createInitialBlock(BeaconStateWithCache state, BeaconBlock oldBlock) {
    Bytes32 blockRoot = oldBlock.signing_root("signature");
    SSZList<Attestation> current_attestations = new SSZList<>(Attestation.class, MAX_ATTESTATIONS);
    final Bytes32 MockStateRoot = Bytes32.ZERO;

    if (state
            .getSlot()
            .compareTo(
                UnsignedLong.valueOf(
                    Constants.GENESIS_SLOT + Constants.MIN_ATTESTATION_INCLUSION_DELAY))
        >= 0) {

      UnsignedLong attestation_slot =
          state.getSlot().minus(UnsignedLong.valueOf(Constants.MIN_ATTESTATION_INCLUSION_DELAY));

      current_attestations =
          blockAttestationsPool.getAggregatedAttestationsForBlockAtSlot(attestation_slot);
    }

    BeaconBlock newBlock =
        StartupUtil.newBeaconBlock(
            state, blockRoot, MockStateRoot, newDeposits, current_attestations);

    return newBlock;
  }

  private void createBlockIfNecessary(BeaconStateWithCache state, BeaconBlock oldBlock) {
    BeaconStateWithCache checkState = BeaconStateWithCache.deepCopy(state);
    try {
      stateTransition.process_slots(checkState, checkState.getSlot().plus(UnsignedLong.ONE), false);
    } catch (SlotProcessingException | EpochProcessingException e) {
      STDOUT.log(Level.FATAL, "Coordinator checking proposer index exception");
    }

    // Calculate the block proposer index, and if we have the
    // block proposer in our set of validators, produce the block
    int proposerIndex = BeaconStateUtil.get_beacon_proposer_index(checkState);
    BLSPublicKey proposer = checkState.getValidators().get(proposerIndex).getPubkey();
    BeaconStateWithCache newState = BeaconStateWithCache.deepCopy(state);
    if (validators.containsKey(proposer)) {
      CompletableFuture<BLSSignature> epochSignatureTask =
          CompletableFuture.supplyAsync(() -> get_epoch_signature(newState, proposer));
      CompletableFuture<BeaconBlock> blockCreationTask =
          CompletableFuture.supplyAsync(() -> createInitialBlock(newState, oldBlock));

      BeaconBlock newBlock;
      try {
        newBlock = blockCreationTask.get();
        BLSSignature epochSignature = epochSignatureTask.get();
        newBlock.getBody().setRandao_reveal(epochSignature);
        List<ProposerSlashing> slashingsInBlock = newBlock.getBody().getProposer_slashings();
        ProposerSlashing slashing = slashings.poll();
        while (slashing != null) {
          if (!state.getValidators().get(slashing.getProposer_index().intValue()).isSlashed()) {
            slashingsInBlock.add(slashing);
          }
          slashing = slashings.poll();
        }
        boolean validate_state_root = false;
        Bytes32 stateRoot =
            stateTransition.initiate(newState, newBlock, validate_state_root).hash_tree_root();
        newBlock.setState_root(stateRoot);
        BLSSignature blockSignature = getBlockSignature(newState, newBlock, proposer);
        newBlock.setSignature(blockSignature);
        this.eventBus.post(newBlock);
        STDOUT.log(Level.DEBUG, "Local validator produced a new block");

        if (validators.get(proposer).isNaughty()) {
          BeaconStateWithCache naughtyState = BeaconStateWithCache.deepCopy(state);
          BeaconBlock newestBlock = createInitialBlock(naughtyState, oldBlock);
          BLSSignature eSignature = epochSignatureTask.get();
          newestBlock.getBody().setRandao_reveal(eSignature);
          Bytes32 sRoot =
              stateTransition
                  .initiate(naughtyState, newestBlock, validate_state_root)
                  .hash_tree_root();
          newestBlock.setState_root(sRoot);
          BLSSignature bSignature = getBlockSignature(naughtyState, newestBlock, proposer);
          newestBlock.setSignature(bSignature);
          this.eventBus.post(newestBlock);
        }
      } catch (InterruptedException | ExecutionException | StateTransitionException e) {
        STDOUT.log(Level.WARN, "Error during block creation " + e.toString());
      }
    }
  }

  private BLSSignature getSignature(Bytes message, Bytes domain, BLSPublicKey signer) {
    SignatureRequest request =
        SignatureRequest.newBuilder()
            .setMessage(ByteString.copyFrom(message.toArray()))
            .setDomain(ByteString.copyFrom(domain.toArray()))
            .build();

    SignatureResponse response;
    response =
        MessageSignerGrpc.newBlockingStub(validators.get(signer).getChannel()).signMessage(request);
    return BLSSignature.fromBytes(Bytes.wrap(response.getMessage().toByteArray()));
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

  public BLSSignature slot_signature(BeaconState state, UnsignedLong slot, BLSPublicKey signer) {
    Bytes domain = get_domain(state, DOMAIN_BEACON_ATTESTER, compute_epoch_at_slot(slot));
    Bytes32 slot_hash =
        HashTreeUtil.hash_tree_root(
            HashTreeUtil.SSZTypes.BASIC, SSZ.encodeUInt64(slot.longValue()));
    return getSignature(slot_hash, domain, signer);
  }

  public boolean is_aggregator(
      BeaconState state,
      UnsignedLong slot,
      UnsignedLong committeeIndex,
      BLSSignature slot_signature) {
    List<Integer> committee = get_beacon_committee(state, slot, committeeIndex);
    UnsignedLong modulo =
        max(
            UnsignedLong.ONE,
            UnsignedLong.valueOf(committee.size()).dividedBy(TARGET_AGGREGATORS_PER_COMMITTEE));
    return (bytes_to_int(Hash.sha2_256(slot_signature.toBytes()).slice(0, 8)) % modulo.longValue())
        == 0;
  }

  private static boolean isGenesisOrEpochStart(UnsignedLong slot) {
    return slot.mod(UnsignedLong.valueOf(SLOTS_PER_EPOCH)).equals(UnsignedLong.ZERO)
        || isGenesis(slot);
  }

  private static boolean isGenesis(UnsignedLong slot) {
    return slot.equals(UnsignedLong.valueOf(GENESIS_SLOT));
  }

  private void updateAttestationAssignments(BeaconState state) {

    // For each validator, using the spec defined get_committee_assignment,
    // get each validators committee assignment. i.e. learn to which
    // committee they belong in this epoch, and when that committee is
    // going to attest.
    validators.forEach(
        (pubKey, validatorInformation) -> {
          Optional<CommitteeAssignment> committeeAssignment =
              CommitteeAssignmentUtil.get_committee_assignment(
                  state,
                  compute_epoch_at_slot(state.getSlot()),
                  validatorInformation.getValidatorIndex());

          // If it exists, use the committee assignment information to update our
          // attestationAssignments map, which maps slots to Lists of AttesterInformation
          // objects, which contain all the information necessary to produce an attestation
          // for the given validator.
          committeeAssignment.ifPresent(
              assignment -> {
                UnsignedLong slot = assignment.getSlot();
                UnsignedLong committeeIndex = assignment.getCommitteeIndex();
                BLSSignature slot_signature = slot_signature(state, slot, pubKey);
                boolean is_aggregator = is_aggregator(state, slot, committeeIndex, slot_signature);

                List<AttesterInformation> attesterInformationInSlot =
                    attestationAssignments.computeIfAbsent(slot, k -> new ArrayList<>());

                List<Integer> committeeIndices = assignment.getCommittee();
                Committee committee = new Committee(committeeIndex, committeeIndices);
                int validatorIndex = validatorInformation.getValidatorIndex();
                int indexIntoCommittee = committeeIndices.indexOf(validatorIndex);

                attesterInformationInSlot.add(
                    new AttesterInformation(
                        validatorIndex,
                        pubKey,
                        indexIntoCommittee,
                        committee,
                        is_aggregator ? Optional.of(slot_signature) : Optional.empty()));
              });
        });
  }

  private void asyncProduceAttestations(
      List<AttesterInformation> attesterInformations,
      BeaconState state,
      AttestationData genericAttestationData) {
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
                            genericAttestationData)));
  }
}
