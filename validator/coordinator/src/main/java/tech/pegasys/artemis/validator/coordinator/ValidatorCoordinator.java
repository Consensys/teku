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

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.StrictMath.toIntExact;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_current_epoch;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_domain;
import static tech.pegasys.artemis.util.bls.BLSVerify.bls_verify;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.UnsignedLong;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.SECP256K1;
import org.apache.tuweni.ssz.SSZ;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.ProposerSlashing;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.datastructures.state.CrosslinkCommittee;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.datastructures.util.AttestationUtil;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.proto.messagesigner.MessageSignerGrpc;
import tech.pegasys.artemis.proto.messagesigner.SignatureRequest;
import tech.pegasys.artemis.proto.messagesigner.SignatureResponse;
import tech.pegasys.artemis.service.serviceutils.ServiceConfig;
import tech.pegasys.artemis.statetransition.GenesisHeadStateEvent;
import tech.pegasys.artemis.statetransition.HeadStateEvent;
import tech.pegasys.artemis.statetransition.StateTransition;
import tech.pegasys.artemis.statetransition.StateTransitionException;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil.SSZTypes;
import tech.pegasys.artemis.validator.client.ValidatorClient;

/** This class coordinates the activity between the validator clients and the the beacon chain */
public class ValidatorCoordinator {
  private static final ALogger LOG = new ALogger(ValidatorCoordinator.class.getName());
  private final EventBus eventBus;
  private StateTransition stateTransition;
  private final Boolean printEnabled = false;
  private SECP256K1.SecretKey nodeIdentity;
  private BeaconState headState;
  private int numValidators;
  private int numNodes;
  private BeaconBlock validatorBlock;
  private ArrayList<Deposit> newDeposits = new ArrayList<>();
  private final HashMap<BLSPublicKey, Pair<BLSKeyPair, Boolean>> validatorSet = new HashMap<>();
  private ChainStorageClient store;
  private HashMap<BLSPublicKey, ManagedChannel> validatorClientChannels = new HashMap<>();
  private LinkedBlockingQueue<ProposerSlashing> slashings = new LinkedBlockingQueue<>();
  private int naughtinessPercentage;

  static final Integer UNPROCESSED_BLOCKS_LENGTH = 100;

  public ValidatorCoordinator(ServiceConfig config, ChainStorageClient store) {
    this.eventBus = config.getEventBus();
    this.eventBus.register(this);
    this.nodeIdentity =
        SECP256K1.SecretKey.fromBytes(Bytes32.fromHexString(config.getConfig().getIdentity()));
    this.numValidators = config.getConfig().getNumValidators();
    this.numNodes = config.getConfig().getNumNodes();
    this.naughtinessPercentage = config.getConfig().getNaughtinessPercentage();
    this.store = store;

    stateTransition = new StateTransition(printEnabled);

    initializeValidators();
  }

  @Subscribe
  public void checkIfIncomingBlockObeysSlashingConditions(BeaconBlock block) {
    int proposerIndex =
        BeaconStateUtil.get_beacon_proposer_index(headState, UnsignedLong.valueOf(block.getSlot()));
    Validator proposer = headState.getValidator_registry().get(proposerIndex);

    checkArgument(
        bls_verify(
            proposer.getPubkey(),
            block.signed_root("signature"),
            block.getSignature(),
            get_domain(
                headState.getFork(), get_current_epoch(headState), Constants.DOMAIN_BEACON_BLOCK)),
        "Proposer signature is invalid");

    BeaconBlockHeader blockHeader =
        new BeaconBlockHeader(
            UnsignedLong.valueOf(block.getSlot()),
            block.getPrevious_block_root(),
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

  @Subscribe
  // TODO: make sure blocks that are produced right even after new slot to be pushed.
  public void onNewSlot(Date date) {
    if (validatorBlock != null) {
      this.eventBus.post(validatorBlock);
      validatorBlock = null;
    }
  }

  @Subscribe
  public void onGenesisHeadStateEvent(GenesisHeadStateEvent genesisHeadStateEvent) {
    onNewHeadStateEvent(
        new HeadStateEvent(
            genesisHeadStateEvent.getHeadState(), genesisHeadStateEvent.getHeadBlock()));
    this.eventBus.post(true);
  }

  @Subscribe
  public void onNewHeadStateEvent(HeadStateEvent headStateEvent) {
    // Retrieve headState and headBlock from event
    BeaconStateWithCache headState = headStateEvent.getHeadState();
    BeaconBlock headBlock = headStateEvent.getHeadBlock();

    List<Triple<BLSPublicKey, Integer, CrosslinkCommittee>> attesters =
        AttestationUtil.getAttesterInformation(headState, validatorSet);
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
    createBlockIfNecessary(headState, headBlock);

    // Save headState to check for slashings
    this.headState = headState;
  }

  private void produceAttestations(
      BeaconState state,
      BLSPublicKey attester,
      int indexIntoCommittee,
      CrosslinkCommittee committee,
      AttestationData genericAttestationData) {
    int arrayLength = Math.toIntExact((committee.getCommittee().size() + 7) / 8);
    Bytes aggregationBitfield =
        AttestationUtil.getAggregationBitfield(indexIntoCommittee, arrayLength);
    Bytes custodyBitfield = AttestationUtil.getCustodyBitfield(arrayLength);
    AttestationData attestationData =
        AttestationUtil.completeAttestationData(
            state, new AttestationData(genericAttestationData), committee);
    Bytes32 attestationMessage = AttestationUtil.getAttestationMessageToSign(attestationData);
    int domain = AttestationUtil.getDomain(state, attestationData);

    BLSSignature signature = getSignature(attestationMessage, domain, attester);
    this.eventBus.post(
        new Attestation(aggregationBitfield, attestationData, custodyBitfield, signature));
  }

  private BLSSignature getEpochSignature(BeaconState state, BLSPublicKey proposer) {
    UnsignedLong slot = state.getSlot().plus(UnsignedLong.ONE);
    UnsignedLong epoch = BeaconStateUtil.slot_to_epoch(slot);

    Bytes32 messageHash =
        HashTreeUtil.hash_tree_root(SSZTypes.BASIC, SSZ.encodeUInt64(epoch.longValue()));
    int domain = get_domain(state.getFork(), epoch, Constants.DOMAIN_RANDAO).intValue();
    return getSignature(messageHash, domain, proposer);
  }

  private BLSSignature getBlockSignature(
      BeaconState state, BeaconBlock block, BLSPublicKey proposer) {
    int domain =
        get_domain(
                state.getFork(),
                BeaconStateUtil.slot_to_epoch(UnsignedLong.valueOf(block.getSlot())),
                Constants.DOMAIN_BEACON_BLOCK)
            .intValue();

    Bytes32 blockRoot = block.signed_root("signature");

    return getSignature(blockRoot, domain, proposer);
  }

  private BeaconBlock createInitialBlock(BeaconStateWithCache state, BeaconBlock oldBlock) {
    Bytes32 blockRoot = oldBlock.signed_root("signature");
    List<Attestation> current_attestations = new ArrayList<>();
    final Bytes32 MockStateRoot = Bytes32.ZERO;

    if (state
            .getSlot()
            .compareTo(
                UnsignedLong.valueOf(
                    Constants.GENESIS_SLOT + Constants.MIN_ATTESTATION_INCLUSION_DELAY))
        >= 0) {

      UnsignedLong attestation_slot =
          state.getSlot().minus(UnsignedLong.valueOf(Constants.MIN_ATTESTATION_INCLUSION_DELAY));

      current_attestations = this.store.getUnprocessedAttestationsUntilSlot(attestation_slot);
    }

    BeaconBlock newBlock =
        DataStructureUtil.newBeaconBlock(
            state.getSlot().plus(UnsignedLong.ONE),
            blockRoot,
            MockStateRoot,
            newDeposits,
            current_attestations);

    return newBlock;
  }

  private void initializeValidators() {
    // Add all validators to validatorSet hashMap
    int nodeCounter = UInt256.fromBytes(nodeIdentity.bytes()).mod(numNodes).intValue();

    int startIndex = nodeCounter * (numValidators / numNodes);
    int endIndex =
        startIndex
            + (numValidators / numNodes - 1)
            + toIntExact(Math.round((double) nodeCounter / Math.max(1, numNodes - 1)));
    endIndex = Math.min(endIndex, numValidators - 1);

    int numValidators = endIndex - startIndex + 1;

    long numNaughtyValidators = Math.round((naughtinessPercentage * numValidators) / 100.0);
    LOG.log(Level.DEBUG, "startIndex: " + startIndex + " endIndex: " + endIndex);
    for (int i = startIndex; i <= endIndex; i++) {
      BLSKeyPair keypair = BLSKeyPair.random(i);
      int port = Constants.VALIDATOR_CLIENT_PORT_BASE + i;
      new ValidatorClient(keypair, port);
      ManagedChannel channel =
          ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build();
      validatorClientChannels.put(keypair.getPublicKey(), channel);
      LOG.log(Level.DEBUG, "i = " + i + ": " + keypair.getPublicKey().toString());
      if (numNaughtyValidators > 0) {
        validatorSet.put(keypair.getPublicKey(), new ImmutablePair<>(keypair, true));
      } else {
        validatorSet.put(keypair.getPublicKey(), new ImmutablePair<>(keypair, false));
      }
      numNaughtyValidators--;
    }
  }

  private void createBlockIfNecessary(BeaconStateWithCache state, BeaconBlock oldBlock) {
    BeaconStateWithCache newState = BeaconStateWithCache.deepCopy(state);

    // Calculate the block proposer index, and if we have the
    // block proposer in our set of validators, produce the block
    Integer proposerIndex =
        BeaconStateUtil.get_beacon_proposer_index(
            newState, newState.getSlot().plus(UnsignedLong.ONE));
    BLSPublicKey proposer = newState.getValidator_registry().get(proposerIndex).getPubkey();
    if (validatorSet.containsKey(proposer)) {
      CompletableFuture<BLSSignature> epochSignatureTask =
          CompletableFuture.supplyAsync(() -> getEpochSignature(newState, proposer));
      CompletableFuture<BeaconBlock> blockCreationTask =
          CompletableFuture.supplyAsync(() -> createInitialBlock(newState, oldBlock));

      BeaconBlock newBlock;
      try {
        newBlock = blockCreationTask.get();
        BLSSignature epochSignature = epochSignatureTask.get();
        newBlock.getBody().setRandao_reveal(epochSignature);
        List<ProposerSlashing> slashingsInBlock = newBlock.getBody().getProposer_slashings();
        slashings.forEach(
            slashing -> {
              if (!state
                  .getValidator_registry()
                  .get(slashing.getProposer_index().intValue())
                  .isSlashed()) {
                slashingsInBlock.add(slashing);
              }
            });
        slashings = new LinkedBlockingQueue<>();
        stateTransition.initiate(newState, newBlock);
        Bytes32 stateRoot = newState.hash_tree_root();
        newBlock.setState_root(stateRoot);
        BLSSignature blockSignature = getBlockSignature(newState, newBlock, proposer);
        newBlock.setSignature(blockSignature);
        validatorBlock = newBlock;

        // If validator set object's right variable is set to true, then the validator is naughty
        if (validatorSet.get(proposer).getRight()) {
          BeaconStateWithCache naughtyState = BeaconStateWithCache.deepCopy(state);
          BeaconBlock newestBlock = createInitialBlock(naughtyState, oldBlock);
          BLSSignature eSignature = epochSignatureTask.get();
          newestBlock.getBody().setRandao_reveal(eSignature);
          stateTransition.initiate(naughtyState, newestBlock);
          Bytes32 sRoot = newState.hash_tree_root();
          newestBlock.setState_root(sRoot);
          BLSSignature bSignature = getBlockSignature(naughtyState, newestBlock, proposer);
          newestBlock.setSignature(bSignature);
          this.eventBus.post(newestBlock);
        }
      } catch (InterruptedException | ExecutionException | StateTransitionException e) {
        LOG.log(Level.WARN, "Error during block creation");
      }
    }
  }

  private BLSSignature getSignature(Bytes message, int domain, BLSPublicKey signer) {
    SignatureRequest request =
        SignatureRequest.newBuilder()
            .setMessage(ByteString.copyFrom(message.toArray()))
            .setDomain(domain)
            .build();

    SignatureResponse response;
    response =
        MessageSignerGrpc.newBlockingStub(validatorClientChannels.get(signer)).signMessage(request);
    return BLSSignature.fromBytes(Bytes.wrap(response.getMessage().toByteArray()));
  }
}
