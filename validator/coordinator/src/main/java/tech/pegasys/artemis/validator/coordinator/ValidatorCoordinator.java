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

import static java.lang.StrictMath.toIntExact;
import static tech.pegasys.artemis.datastructures.Constants.DOMAIN_ATTESTATION;
import static tech.pegasys.artemis.datastructures.Constants.GENESIS_SLOT;
import static tech.pegasys.artemis.datastructures.Constants.MAX_VALIDATORS_PER_COMMITTEE;
import static tech.pegasys.artemis.datastructures.Constants.SLOTS_PER_EPOCH;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_epoch_of_slot;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_domain;
import static tech.pegasys.artemis.statetransition.StateTransition.process_slots;
import static tech.pegasys.artemis.statetransition.util.ForkChoiceUtil.get_head;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.UnsignedLong;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.IntStream;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.MutableTriple;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import org.apache.tuweni.crypto.SECP256K1;
import org.apache.tuweni.ssz.SSZ;
import org.apache.tuweni.units.bigints.UInt256;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
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
import tech.pegasys.artemis.datastructures.util.MockStartValidatorKeyPairFactory;
import tech.pegasys.artemis.proto.messagesigner.MessageSignerGrpc;
import tech.pegasys.artemis.proto.messagesigner.SignatureRequest;
import tech.pegasys.artemis.proto.messagesigner.SignatureResponse;
import tech.pegasys.artemis.service.serviceutils.ServiceConfig;
import tech.pegasys.artemis.statetransition.GenesisStateEvent;
import tech.pegasys.artemis.statetransition.SlotEvent;
import tech.pegasys.artemis.statetransition.StateTransition;
import tech.pegasys.artemis.statetransition.StateTransitionException;
import tech.pegasys.artemis.statetransition.ValidatorAssignmentEvent;
import tech.pegasys.artemis.statetransition.util.EpochProcessingException;
import tech.pegasys.artemis.statetransition.util.SlotProcessingException;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.Store;
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.artemis.util.alogger.ALogger.Color;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil.SSZTypes;
import tech.pegasys.artemis.util.mikuli.KeyPair;
import tech.pegasys.artemis.util.mikuli.SecretKey;
import tech.pegasys.artemis.validator.client.ValidatorClient;
import tech.pegasys.artemis.validator.client.ValidatorClientUtil;

/** This class coordinates the activity between the validator clients and the the beacon chain */
public class ValidatorCoordinator {
  private static final ALogger LOG = new ALogger(ValidatorCoordinator.class.getName());
  private static final ALogger STDOUT = new ALogger("stdout");
  private final EventBus eventBus;
  private StateTransition stateTransition;
  private final Boolean printEnabled = false;
  private SECP256K1.SecretKey nodeIdentity;
  private BeaconState headState;
  private int numValidators;
  private int numNodes;
  private BeaconBlock validatorBlock;
  private ArrayList<Deposit> newDeposits = new ArrayList<>();
  private final HashMap<BLSPublicKey, MutableTriple<BLSKeyPair, Boolean, Integer>> validatorSet =
      new HashMap<>();
  private ChainStorageClient chainStorageClient;
  private HashMap<BLSPublicKey, ManagedChannel> validatorClientChannels = new HashMap<>();
  private HashMap<UnsignedLong, List<Triple<List<Integer>, UnsignedLong, Integer>>>
      committeeAssignments = new HashMap<>();
  private LinkedBlockingQueue<ProposerSlashing> slashings = new LinkedBlockingQueue<>();
  private int naughtinessPercentage;

  static final Integer UNPROCESSED_BLOCKS_LENGTH = 100;

  @SuppressWarnings("unchecked")
  public ValidatorCoordinator(ServiceConfig config, ChainStorageClient store) {
    this.eventBus = config.getEventBus();
    this.eventBus.register(this);
    this.nodeIdentity =
        SECP256K1.SecretKey.fromBytes(Bytes32.fromHexString(config.getConfig().getIdentity()));
    this.naughtinessPercentage = config.getConfig().getNaughtinessPercentage();
    this.numNodes = config.getConfig().getNumNodes();
    this.numValidators = config.getConfig().getNumValidators();
    this.chainStorageClient = store;

    stateTransition = new StateTransition(printEnabled);

    initializeValidators2(config.getConfig());
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
      this.eventBus.post(validatorBlock);
      validatorBlock = null;
    }
  }

  @Subscribe
  public void onGenesisStateEvent(GenesisStateEvent genesisHeadStateEvent) {
    BeaconBlock genesisBlock = genesisHeadStateEvent.getGenesisBlock();
    BeaconStateWithCache genesisState = genesisHeadStateEvent.getGenesisState();

    // Get validator indices of our own validators
    List<Validator> validatorRegistry = genesisState.getValidators();
    IntStream.range(0, validatorRegistry.size())
        .forEach(
            i -> {
              if (validatorSet.keySet().contains(validatorRegistry.get(i).getPubkey())) {
                validatorSet.get(validatorRegistry.get(i).getPubkey()).setRight(i);
              }
            });
  }

  @Subscribe
  public void onNewAssignment(ValidatorAssignmentEvent validatorAssignmentEvent)
      throws IllegalArgumentException {
    Store store = chainStorageClient.getStore();
    Bytes32 headBlockRoot = get_head(store);
    BeaconBlock headBlock = store.getBlocks().get(headBlockRoot);
    BeaconState headState = store.getBlock_states().get(headBlockRoot);

    // Logging
    STDOUT.log(
        Level.INFO,
        "Head block slot:" + "                       " + headBlock.getSlot().longValue());
    STDOUT.log(
        Level.INFO,
        "Justified epoch:"
            + "                       "
            + store.getJustified_checkpoint().getEpoch());
    STDOUT.log(
        Level.INFO,
        "Finalized epoch:"
            + "                       "
            + store.getFinalized_checkpoint().getEpoch());

    try {

      if (headState.getSlot().mod(UnsignedLong.valueOf(SLOTS_PER_EPOCH)).equals(UnsignedLong.ZERO)
          || headState.getSlot().equals(UnsignedLong.valueOf(GENESIS_SLOT))) {
        validatorSet.forEach(
            (pubKey, validatorInformation) -> {
              Optional<Triple<List<Integer>, UnsignedLong, UnsignedLong>> committeeAssignment =
                  ValidatorClientUtil.get_committee_assignment(
                      headState,
                      compute_epoch_of_slot(headState.getSlot()),
                      validatorInformation.getRight());
              committeeAssignment.ifPresent(
                  assignment -> {
                    UnsignedLong slot = assignment.getRight();
                    List<Triple<List<Integer>, UnsignedLong, Integer>> assignmentsInSlot =
                        committeeAssignments.get(slot);
                    if (assignmentsInSlot == null) {
                      assignmentsInSlot = new ArrayList<>();
                      committeeAssignments.put(slot, assignmentsInSlot);
                    }
                    assignmentsInSlot.add(
                        new MutableTriple<>(
                            assignment.getLeft(),
                            assignment.getMiddle(),
                            validatorInformation.getRight()));
                  });
            });
      }

      List<Triple<BLSPublicKey, Integer, CrosslinkCommittee>> attesters =
          AttestationUtil.getAttesterInformation(headState, committeeAssignments);
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
      createBlockIfNecessary((BeaconStateWithCache) headState, headBlock);

      // Save headState to check for slashings
      this.headState = headState;
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
    Bytes aggregationBitfield = AttestationUtil.getAggregationBits(indexIntoCommittee);
    Bytes custodyBits = Bytes.wrap(new byte[MAX_VALIDATORS_PER_COMMITTEE / 8]);
    AttestationData attestationData =
        AttestationUtil.completeAttestationCrosslinkData(
            state, new AttestationData(genericAttestationData), committee);
    Bytes32 attestationMessage = AttestationUtil.getAttestationMessageToSign(attestationData);
    Bytes domain = get_domain(state, DOMAIN_ATTESTATION, attestationData.getTarget().getEpoch());

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
    UnsignedLong epoch = BeaconStateUtil.compute_epoch_of_slot(slot);

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
            BeaconStateUtil.compute_epoch_of_slot(block.getSlot()));

    Bytes32 blockRoot = block.signing_root("signature");

    return getSignature(blockRoot, domain, proposer);
  }

  private BeaconBlock createInitialBlock(BeaconStateWithCache state, BeaconBlock oldBlock) {
    Bytes32 blockRoot = oldBlock.signing_root("signature");
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

      current_attestations =
          this.chainStorageClient.getUnprocessedAttestationsUntilSlot(state, attestation_slot);
    }

    BeaconBlock newBlock =
        DataStructureUtil.newBeaconBlock(
            state.getSlot().plus(UnsignedLong.ONE),
            blockRoot,
            MockStateRoot,
            newDeposits,
            current_attestations,
            numValidators);

    return newBlock;
  }

  @SuppressWarnings("unchecked")
  private void initializeValidators(ArtemisConfiguration config) {
    Pair<Integer, Integer> startAndEnd = getStartAndEnd();
    int startIndex = startAndEnd.getLeft();
    int endIndex = startAndEnd.getRight();
    long numNaughtyValidators = Math.round((naughtinessPercentage * numValidators) / 100.0);
    List<BLSKeyPair> keypairs = new ArrayList<>();
    if (config.getInteropActive()) {
      try {
        Path path = Paths.get(config.getInteropInputFile());
        String read = Files.readAllLines(path).get(0);
        JSONParser parser = new JSONParser();
        Object obj = parser.parse(read);
        JSONObject array = (JSONObject) obj;
        JSONArray privateKeyStrings = (JSONArray) array.get("privateKeys");
        for (int i = startIndex; i <= endIndex; i++) {
          BLSKeyPair keypair =
              new BLSKeyPair(
                  new KeyPair(
                      SecretKey.fromBytes(
                          Bytes.fromHexString(privateKeyStrings.get(i).toString()))));
          keypairs.add(keypair);
        }
      } catch (IOException | ParseException e) {
        STDOUT.log(Level.FATAL, e.toString());
      }
    } else {
      for (int i = startIndex; i <= endIndex; i++) {
        BLSKeyPair keypair = BLSKeyPair.random(i);
        keypairs.add(keypair);
      }
    }
    int our_index = 0;
    for (int i = startIndex; i <= endIndex; i++) {
      BLSKeyPair keypair = keypairs.get(our_index);
      int port = Constants.VALIDATOR_CLIENT_PORT_BASE + i;
      new ValidatorClient(keypair, port);
      ManagedChannel channel =
          ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build();
      validatorClientChannels.put(keypair.getPublicKey(), channel);
      STDOUT.log(Level.DEBUG, "i = " + i + ": " + keypair.getPublicKey().toString());
      if (numNaughtyValidators > 0) {
        validatorSet.put(keypair.getPublicKey(), new MutableTriple<>(keypair, true, -1));
      } else {
        validatorSet.put(keypair.getPublicKey(), new MutableTriple<>(keypair, false, -1));
      }
      numNaughtyValidators--;
      our_index++;
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private void initializeValidators2(ArtemisConfiguration config) {
    Pair<Integer, Integer> startAndEnd = getStartAndEnd();
    int startIndex = startAndEnd.getLeft();
    int endIndex = startAndEnd.getRight();
    long numNaughtyValidators = Math.round((naughtinessPercentage * numValidators) / 100.0);
    List<BLSKeyPair> keypairs = new ArrayList<>();
    if (config.getInteropActive()) {
      switch (config.getInteropMode()) {
        case Constants.FILE_INTEROP:
          try {
            Path path = Paths.get(config.getInteropInputFile());
            String yaml = Files.readString(path.toAbsolutePath(), StandardCharsets.US_ASCII);
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            Map<String, List<Map>> fileMap =
                mapper.readValue(yaml, new TypeReference<Map<String, List<Map>>>() {});
            List<Map> depositdatakeys = fileMap.get("DepositDataKeys");
            List<String> privateKeyStrings = new ArrayList<>();
            depositdatakeys.forEach(
                item -> {
                  privateKeyStrings.add(item.get("Privkey").toString());
                });
            for (int i = startIndex; i <= endIndex; i++) {
              BLSKeyPair keypair =
                  new BLSKeyPair(
                      new KeyPair(
                          SecretKey.fromBytes(
                              Bytes48.leftPad(Bytes.fromBase64String(privateKeyStrings.get(i))))));
              keypairs.add(keypair);
            }
          } catch (IOException e) {
            STDOUT.log(Level.FATAL, e.toString());
          }
          break;
        case Constants.MOCKED_START_INTEROP:
          startIndex = config.getInteropOwnedValidatorStartIndex();
          // - 1 because endIndex is inclusive
          endIndex = startIndex + config.getInteropOwnedValidatorCount() - 1;
          STDOUT.log(
              Level.INFO, "Owning validator range " + startIndex + " to " + endIndex, Color.GREEN);
          keypairs = new MockStartValidatorKeyPairFactory().generateKeyPairs(startIndex, endIndex);
          break;
      }
    } else {
      for (int i = startIndex; i <= endIndex; i++) {
        BLSKeyPair keypair = BLSKeyPair.random(i);
        keypairs.add(keypair);
      }
    }
    int our_index = 0;
    for (int i = startIndex; i <= endIndex; i++) {
      BLSKeyPair keypair = keypairs.get(our_index);
      int port = Constants.VALIDATOR_CLIENT_PORT_BASE + i;
      new ValidatorClient(keypair, port);
      ManagedChannel channel =
          ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build();
      validatorClientChannels.put(keypair.getPublicKey(), channel);
      STDOUT.log(Level.DEBUG, "i = " + i + ": " + keypair.getPublicKey().toString());
      if (numNaughtyValidators > 0) {
        validatorSet.put(keypair.getPublicKey(), new MutableTriple<>(keypair, true, -1));
      } else {
        validatorSet.put(keypair.getPublicKey(), new MutableTriple<>(keypair, false, -1));
      }
      numNaughtyValidators--;
      our_index++;
    }
  }

  private void createBlockIfNecessary(BeaconStateWithCache state, BeaconBlock oldBlock) {
    BeaconStateWithCache checkState = BeaconStateWithCache.deepCopy(state);
    try {
      process_slots(checkState, checkState.getSlot().plus(UnsignedLong.ONE), false);
    } catch (SlotProcessingException | EpochProcessingException e) {
      STDOUT.log(Level.FATAL, "Coordinator checking proposer index exception");
    }

    // Calculate the block proposer index, and if we have the
    // block proposer in our set of validators, produce the block
    int proposerIndex = BeaconStateUtil.get_beacon_proposer_index(checkState);
    BLSPublicKey proposer = checkState.getValidators().get(proposerIndex).getPubkey();

    BeaconStateWithCache newState = BeaconStateWithCache.deepCopy(state);
    if (validatorSet.containsKey(proposer)) {
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
        slashings.forEach(
            slashing -> {
              if (!state.getValidators().get(slashing.getProposer_index().intValue()).isSlashed()) {
                slashingsInBlock.add(slashing);
              }
            });
        slashings = new LinkedBlockingQueue<>();
        boolean validate_state_root = false;
        Bytes32 stateRoot =
            stateTransition.initiate(newState, newBlock, validate_state_root).hash_tree_root();
        newBlock.setState_root(stateRoot);
        BLSSignature blockSignature = getBlockSignature(newState, newBlock, proposer);
        newBlock.setSignature(blockSignature);
        validatorBlock = newBlock;

        // If validator set object's right variable is set to true, then the validator is naughty
        if (validatorSet.get(proposer).getMiddle()) {
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
        MessageSignerGrpc.newBlockingStub(validatorClientChannels.get(signer)).signMessage(request);
    return BLSSignature.fromBytes(Bytes.wrap(response.getMessage().toByteArray()));
  }

  private Pair<Integer, Integer> getStartAndEnd() {
    // Add all validators to validatorSet hashMap
    int nodeCounter = UInt256.fromBytes(nodeIdentity.bytes()).mod(numNodes).intValue();

    int startIndex = nodeCounter * (numValidators / numNodes);
    int endIndex =
        startIndex
            + (numValidators / numNodes - 1)
            + toIntExact(Math.round((double) nodeCounter / Math.max(1, numNodes - 1)));
    endIndex = Math.min(endIndex, numValidators - 1);

    int numValidators = endIndex - startIndex + 1;

    LOG.log(Level.INFO, "startIndex: " + startIndex + " endIndex: " + endIndex);
    return new ImmutablePair<>(startIndex, endIndex);
  }
}
