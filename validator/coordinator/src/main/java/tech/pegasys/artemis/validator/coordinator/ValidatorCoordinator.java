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
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_domain;
import static tech.pegasys.artemis.util.config.Constants.DOMAIN_BEACON_ATTESTER;
import static tech.pegasys.artemis.util.config.Constants.GENESIS_SLOT;
import static tech.pegasys.artemis.util.config.Constants.MAX_ATTESTATIONS;
import static tech.pegasys.artemis.util.config.Constants.MAX_DEPOSITS;
import static tech.pegasys.artemis.util.config.Constants.MAX_VALIDATORS_PER_COMMITTEE;
import static tech.pegasys.artemis.util.config.Constants.SLOTS_PER_EPOCH;
import static tech.pegasys.artemis.validator.coordinator.ValidatorLoader.initializeValidators;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.UnsignedLong;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import org.apache.commons.lang3.tuple.MutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.ProposerSlashing;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.datastructures.state.CrosslinkCommittee;
import tech.pegasys.artemis.datastructures.util.AttestationUtil;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.datastructures.util.DepositUtil;
import tech.pegasys.artemis.proto.messagesigner.MessageSignerGrpc;
import tech.pegasys.artemis.proto.messagesigner.SignatureRequest;
import tech.pegasys.artemis.proto.messagesigner.SignatureResponse;
import tech.pegasys.artemis.statetransition.StateTransition;
import tech.pegasys.artemis.statetransition.StateTransitionException;
import tech.pegasys.artemis.statetransition.events.ValidatorAssignmentEvent;
import tech.pegasys.artemis.statetransition.util.EpochProcessingException;
import tech.pegasys.artemis.statetransition.util.SlotProcessingException;
import tech.pegasys.artemis.statetransition.util.StartupUtil;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.Store;
import tech.pegasys.artemis.storage.events.SlotEvent;
import tech.pegasys.artemis.util.SSZTypes.Bitlist;
import tech.pegasys.artemis.util.SSZTypes.SSZList;
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;
import tech.pegasys.artemis.util.config.Constants;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil.SSZTypes;
import tech.pegasys.artemis.validator.client.ValidatorClientUtil;

/** This class coordinates the activity between the validator clients and the the beacon chain */
public class ValidatorCoordinator {
  private static final ALogger STDOUT = new ALogger("stdout");
  private final EventBus eventBus;
  private final Map<BLSPublicKey, ValidatorInfo> validators;
  private StateTransition stateTransition;
  //  private BeaconState headState;
  private BeaconBlock validatorBlock;
  private SSZList<Deposit> newDeposits = new SSZList<>(Deposit.class, MAX_DEPOSITS);
  private ChainStorageClient chainStorageClient;
  private HashMap<UnsignedLong, List<Triple<List<Integer>, UnsignedLong, Integer>>>
      committeeAssignments = new HashMap<>();
  private LinkedBlockingQueue<ProposerSlashing> slashings = new LinkedBlockingQueue<>();

  public ValidatorCoordinator(
      EventBus eventBus, ChainStorageClient chainStorageClient, ArtemisConfiguration config) {
    this.eventBus = eventBus;
    this.chainStorageClient = chainStorageClient;
    this.stateTransition = new StateTransition(false);
    this.validators = initializeValidators(config, chainStorageClient);
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
    if (validatorBlock != null) {
      STDOUT.log(Level.DEBUG, "Local validator produced a new block");
      this.eventBus.post(validatorBlock);
      validatorBlock = null;
    }
  }

  @Subscribe
  public void onNewDeposit(tech.pegasys.artemis.pow.event.Deposit event) {
    STDOUT.log(Level.DEBUG, "New deposit received by ValidatorCoordinator");
    Deposit deposit = DepositUtil.convertDepositEventToOperationDeposit(event);
    if (!newDeposits.contains(deposit)) newDeposits.add(deposit);
  }

  @Subscribe
  public void onNewAssignment(ValidatorAssignmentEvent event) throws IllegalArgumentException {
    try {
      Store store = chainStorageClient.getStore();
      BeaconBlock headBlock = store.getBlock(event.getHeadBlockRoot());
      BeaconState headState = store.getBlockState(event.getHeadBlockRoot());
      if (headState.getSlot().mod(UnsignedLong.valueOf(SLOTS_PER_EPOCH)).equals(UnsignedLong.ZERO)
          || headState.getSlot().equals(UnsignedLong.valueOf(GENESIS_SLOT))) {
        validators.forEach(
            (pubKey, validatorInformation) -> {
              Optional<Triple<List<Integer>, UnsignedLong, UnsignedLong>> committeeAssignment =
                  ValidatorClientUtil.get_committee_assignment(
                      headState,
                      compute_epoch_at_slot(headState.getSlot()),
                      validatorInformation.getValidatorIndex());
              committeeAssignment.ifPresent(
                  assignment -> {
                    UnsignedLong slot = assignment.getRight();
                    List<Triple<List<Integer>, UnsignedLong, Integer>> assignmentsInSlot =
                        committeeAssignments.computeIfAbsent(slot, k -> new ArrayList<>());
                    assignmentsInSlot.add(
                        new MutableTriple<>(
                            assignment.getLeft(),
                            assignment.getMiddle(),
                            validatorInformation.getValidatorIndex()));
                  });
            });
      }

      List<Triple<BLSPublicKey, Integer, CrosslinkCommittee>> attesters =
          AttestationUtil.getAttesterInformation(headState, committeeAssignments);
      // TODO: 0.9.0 We need to set the index on this data somewhere
      AttestationData genericAttestationData =
          AttestationUtil.getGenericAttestationData(headState, headBlock);

      CompletableFuture.runAsync(
          () ->
              attesters
                  .parallelStream()
                  .forEach(
                      attesterInfo ->
                          produceAttestations(
                              headState,
                              attesterInfo.getLeft(),
                              attesterInfo.getMiddle(),
                              attesterInfo.getRight(),
                              genericAttestationData)));

      // Copy state so that state transition during block creation does not manipulate headState in
      // storage
      createBlockIfNecessary(BeaconStateWithCache.fromBeaconState(headState), headBlock);

      // Save headState to check for slashings
      //      this.headState = headState;
    } catch (IllegalArgumentException e) {
      STDOUT.log(Level.WARN, "Can not produce attestations or create a block" + e.toString());
    }
  }

  private void produceAttestations(
      BeaconState state,
      BLSPublicKey attester,
      int indexIntoCommittee,
      CrosslinkCommittee committee,
      AttestationData genericAttestationData) {
    int commmitteSize = committee.getCommitteeSize();
    genericAttestationData.setIndex(committee.getIndex());
    Bitlist aggregationBitfield =
        AttestationUtil.getAggregationBits(commmitteSize, indexIntoCommittee);
    Bitlist custodyBits = new Bitlist(commmitteSize, MAX_VALIDATORS_PER_COMMITTEE);
    AttestationData attestationData =
        AttestationUtil.completeAttestationCrosslinkData(
            state, new AttestationData(genericAttestationData), committee);
    Bytes32 attestationMessage = AttestationUtil.getAttestationMessageToSign(attestationData);
    Bytes domain =
        get_domain(state, DOMAIN_BEACON_ATTESTER, attestationData.getTarget().getEpoch());

    BLSSignature signature = getSignature(attestationMessage, domain, attester);
    this.eventBus.post(
        new Attestation(aggregationBitfield, attestationData, custodyBits, signature));
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
          this.chainStorageClient.getUnprocessedAttestationsUntilSlot(state, attestation_slot);
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
        validatorBlock = newBlock;

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
        STDOUT.log(Level.WARN, "Error during block creation" + e.toString());
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
}
