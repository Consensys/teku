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

package tech.pegasys.teku.datastructures.util;

import static java.lang.Math.toIntExact;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_domain;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_signing_root;
import static tech.pegasys.teku.util.config.Constants.DOMAIN_DEPOSIT;
import static tech.pegasys.teku.util.config.Constants.EPOCHS_PER_ETH1_VOTING_PERIOD;
import static tech.pegasys.teku.util.config.Constants.SLOTS_PER_EPOCH;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.LongStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockBody;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.EnrForkId;
import tech.pegasys.teku.datastructures.operations.AggregateAndProof;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.AttestationData;
import tech.pegasys.teku.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.datastructures.operations.Deposit;
import tech.pegasys.teku.datastructures.operations.DepositData;
import tech.pegasys.teku.datastructures.operations.DepositMessage;
import tech.pegasys.teku.datastructures.operations.DepositWithIndex;
import tech.pegasys.teku.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.state.Fork;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.datastructures.state.PendingAttestation;
import tech.pegasys.teku.datastructures.state.Validator;
import tech.pegasys.teku.ssz.SSZTypes.Bitlist;
import tech.pegasys.teku.ssz.SSZTypes.Bitvector;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.ssz.SSZTypes.SSZMutableList;
import tech.pegasys.teku.ssz.SSZTypes.SSZMutableVector;
import tech.pegasys.teku.ssz.SSZTypes.SSZVector;
import tech.pegasys.teku.util.config.Constants;

public final class DataStructureUtil {

  private int seed;
  private Supplier<BLSPublicKey> pubKeyGenerator = () -> BLSPublicKey.random(nextSeed());

  public DataStructureUtil() {
    this(92892824);
  }

  public DataStructureUtil(final int seed) {
    this.seed = seed;
  }

  public DataStructureUtil withPubKeyGenerator(Supplier<BLSPublicKey> pubKeyGenerator) {
    this.pubKeyGenerator = pubKeyGenerator;
    return this;
  }

  private int nextSeed() {
    return seed++;
  }

  public long randomLong() {
    return new Random(nextSeed()).nextLong();
  }

  public UnsignedLong randomUnsignedLong() {
    return UnsignedLong.fromLongBits(randomLong());
  }

  private Bytes4 randomBytes4() {
    return new Bytes4(randomBytes32().slice(0, 4));
  }

  public Bytes32 randomBytes32() {
    final Random random = new Random(nextSeed());
    return Bytes32.random(random);
  }

  public BLSSignature randomSignature() {
    return BLSSignature.random(nextSeed());
  }

  public <T> SSZList<T> randomSSZList(
      Class<? extends T> classInfo, long maxSize, Supplier<T> valueGenerator) {
    return randomSSZList(classInfo, maxSize / 10, maxSize, valueGenerator);
  }

  public <T> SSZList<T> randomFullSSZList(
      Class<? extends T> classInfo, long maxSize, Supplier<T> valueGenerator) {
    return randomSSZList(classInfo, maxSize, maxSize, valueGenerator);
  }

  public <T> SSZList<T> randomSSZList(
      Class<? extends T> classInfo, final long numItems, long maxSize, Supplier<T> valueGenerator) {
    SSZMutableList<T> sszList = SSZList.createMutable(classInfo, maxSize);
    LongStream.range(0, numItems).forEach(i -> sszList.add(valueGenerator.get()));
    return sszList;
  }

  public <T> SSZVector<T> randomSSZVector(
      T defaultClassObject, long maxSize, Supplier<T> valueGenerator) {
    SSZMutableVector<T> sszvector =
        SSZVector.createMutable(toIntExact(maxSize), defaultClassObject);
    long numItems = maxSize / 10;
    LongStream.range(0, numItems).forEach(i -> sszvector.set(toIntExact(i), valueGenerator.get()));
    return sszvector;
  }

  public Bitlist randomBitlist() {
    return randomBitlist(Constants.MAX_VALIDATORS_PER_COMMITTEE);
  }

  public Bitlist randomBitlist(int n) {
    Bitlist bitlist = new Bitlist(n, n);
    Random random = new Random(nextSeed());

    for (int i = 0; i < n; i++) {
      if (random.nextBoolean()) {
        bitlist.setBit(i);
      }
    }
    return bitlist;
  }

  public Bitvector randomBitvector(int n) {
    BitSet bitSet = new BitSet(n);
    Random random = new Random(nextSeed());

    for (int i = 0; i < n; i++) {
      if (random.nextBoolean()) {
        bitSet.set(i);
      }
    }
    return new Bitvector(bitSet, n);
  }

  public BLSPublicKey randomPublicKey() {
    return pubKeyGenerator.get();
  }

  public Eth1Data randomEth1Data() {
    return new Eth1Data(randomBytes32(), randomUnsignedLong(), randomBytes32());
  }

  public Checkpoint randomCheckpoint() {
    return new Checkpoint(randomUnsignedLong(), randomBytes32());
  }

  public AttestationData randomAttestationData() {
    return randomAttestationData(randomUnsignedLong());
  }

  public AttestationData randomAttestationData(final UnsignedLong slot) {
    return new AttestationData(
        slot, randomUnsignedLong(), randomBytes32(), randomCheckpoint(), randomCheckpoint());
  }

  public Attestation randomAttestation() {
    return new Attestation(randomBitlist(), randomAttestationData(), randomSignature());
  }

  public AggregateAndProof randomAggregateAndProof() {
    return new AggregateAndProof(randomUnsignedLong(), randomAttestation(), randomSignature());
  }

  public SignedAggregateAndProof randomSignedAggregateAndProof() {
    return new SignedAggregateAndProof(randomAggregateAndProof(), randomSignature());
  }

  public VoteTracker randomVoteTracker() {
    return new VoteTracker(randomBytes32(), randomBytes32(), randomUnsignedLong());
  }

  public PendingAttestation randomPendingAttestation() {
    return new PendingAttestation(
        randomBitlist(), randomAttestationData(), randomUnsignedLong(), randomUnsignedLong());
  }

  public AttesterSlashing randomAttesterSlashing() {
    return new AttesterSlashing(randomIndexedAttestation(), randomIndexedAttestation());
  }

  public SignedBeaconBlock randomSignedBeaconBlock(long slotNum) {
    return randomSignedBeaconBlock(UnsignedLong.valueOf(slotNum));
  }

  public SignedBeaconBlock randomSignedBeaconBlock(UnsignedLong slotNum) {
    final BeaconBlock beaconBlock = randomBeaconBlock(slotNum);
    return new SignedBeaconBlock(beaconBlock, randomSignature());
  }

  public SignedBeaconBlock randomSignedBeaconBlock(long slotNum, Bytes32 parentRoot) {
    return randomSignedBeaconBlock(slotNum, parentRoot, false);
  }

  public SignedBeaconBlock randomSignedBeaconBlock(long slotNum, Bytes32 parentRoot, boolean full) {
    final BeaconBlock beaconBlock = randomBeaconBlock(slotNum, parentRoot, full);
    return new SignedBeaconBlock(beaconBlock, randomSignature());
  }

  public SignedBeaconBlock randomSignedBeaconBlock(long slotNum, BeaconState state) {
    return randomSignedBeaconBlock(UnsignedLong.valueOf(slotNum), state);
  }

  public SignedBeaconBlock randomSignedBeaconBlock(UnsignedLong slotNum, BeaconState state) {
    final BeaconBlockBody body = randomBeaconBlockBody();
    final Bytes32 stateRoot = state.hash_tree_root();

    final BeaconBlock block =
        new BeaconBlock(slotNum, randomUnsignedLong(), randomBytes32(), stateRoot, body);
    return new SignedBeaconBlock(block, randomSignature());
  }

  public BeaconBlock randomBeaconBlock(long slotNum) {
    return randomBeaconBlock(UnsignedLong.valueOf(slotNum));
  }

  public BeaconBlock randomBeaconBlock(UnsignedLong slotNum) {
    final UnsignedLong proposer_index = randomUnsignedLong();
    Bytes32 previous_root = randomBytes32();
    Bytes32 state_root = randomBytes32();
    BeaconBlockBody body = randomBeaconBlockBody();

    return new BeaconBlock(slotNum, proposer_index, previous_root, state_root, body);
  }

  public SignedBlockAndState randomSignedBlockAndState(final UnsignedLong slot) {
    final BeaconBlockAndState blockAndState = randomBlockAndState(slot);

    final SignedBeaconBlock signedBlock =
        new SignedBeaconBlock(blockAndState.getBlock(), randomSignature());
    return new SignedBlockAndState(signedBlock, blockAndState.getState());
  }

  public BeaconBlockAndState randomBlockAndState(final long slot, final BeaconState beaconState) {
    final UnsignedLong unsignedSlot = UnsignedLong.valueOf(slot);
    final BeaconState state = beaconState.updated(b -> b.setSlot(unsignedSlot));
    return randomBlockAndState(unsignedSlot, state);
  }

  public BeaconBlockAndState randomBlockAndState(final UnsignedLong slot) {
    final BeaconState state = randomBeaconState(slot);
    return randomBlockAndState(slot, state);
  }

  public BeaconBlockAndState randomBlockAndState(final UnsignedLong slot, final BeaconState state) {
    final Bytes32 parentRoot = randomBytes32();
    final Bytes32 state_root = state.hash_tree_root();
    final BeaconBlockBody body = randomBeaconBlockBody();
    final UnsignedLong proposer_index = randomUnsignedLong();
    final BeaconBlock block = new BeaconBlock(slot, proposer_index, parentRoot, state_root, body);

    return new BeaconBlockAndState(block, state);
  }

  public BeaconBlock randomBeaconBlock(long slotNum, Bytes32 parentRoot, boolean isFull) {
    UnsignedLong slot = UnsignedLong.valueOf(slotNum);

    final UnsignedLong proposer_index = randomUnsignedLong();
    Bytes32 state_root = randomBytes32();
    BeaconBlockBody body = !isFull ? randomBeaconBlockBody() : randomFullBeaconBlockBody();

    return new BeaconBlock(slot, proposer_index, parentRoot, state_root, body);
  }

  public BeaconBlock randomBeaconBlock(long slotNum, Bytes32 parentRoot) {
    return randomBeaconBlock(slotNum, parentRoot, false);
  }

  public SignedBeaconBlockHeader randomSignedBeaconBlockHeader() {
    return new SignedBeaconBlockHeader(randomBeaconBlockHeader(), randomSignature());
  }

  public BeaconBlockHeader randomBeaconBlockHeader() {
    return new BeaconBlockHeader(
        randomUnsignedLong(),
        randomUnsignedLong(),
        randomBytes32(),
        randomBytes32(),
        randomBytes32());
  }

  public BeaconBlockBody randomBeaconBlockBody() {
    return new BeaconBlockBody(
        randomSignature(),
        randomEth1Data(),
        Bytes32.ZERO,
        randomSSZList(
            ProposerSlashing.class, Constants.MAX_PROPOSER_SLASHINGS, this::randomProposerSlashing),
        randomSSZList(
            AttesterSlashing.class, Constants.MAX_ATTESTER_SLASHINGS, this::randomAttesterSlashing),
        randomSSZList(Attestation.class, Constants.MAX_ATTESTATIONS, this::randomAttestation),
        randomSSZList(Deposit.class, Constants.MAX_DEPOSITS, this::randomDepositWithoutIndex),
        randomSSZList(
            SignedVoluntaryExit.class,
            Constants.MAX_VOLUNTARY_EXITS,
            this::randomSignedVoluntaryExit));
  }

  public BeaconBlockBody randomFullBeaconBlockBody() {
    return new BeaconBlockBody(
        randomSignature(),
        randomEth1Data(),
        Bytes32.ZERO,
        randomFullSSZList(
            ProposerSlashing.class, Constants.MAX_PROPOSER_SLASHINGS, this::randomProposerSlashing),
        randomFullSSZList(
            AttesterSlashing.class, Constants.MAX_ATTESTER_SLASHINGS, this::randomAttesterSlashing),
        randomFullSSZList(Attestation.class, Constants.MAX_ATTESTATIONS, this::randomAttestation),
        randomFullSSZList(Deposit.class, Constants.MAX_DEPOSITS, this::randomDepositWithoutIndex),
        randomFullSSZList(
            SignedVoluntaryExit.class,
            Constants.MAX_VOLUNTARY_EXITS,
            this::randomSignedVoluntaryExit));
  }

  public ProposerSlashing randomProposerSlashing() {
    return new ProposerSlashing(randomSignedBeaconBlockHeader(), randomSignedBeaconBlockHeader());
  }

  public IndexedAttestation randomIndexedAttestation() {
    SSZMutableList<UnsignedLong> attesting_indices =
        SSZList.createMutable(UnsignedLong.class, Constants.MAX_VALIDATORS_PER_COMMITTEE);
    attesting_indices.add(randomUnsignedLong());
    attesting_indices.add(randomUnsignedLong());
    attesting_indices.add(randomUnsignedLong());
    return new IndexedAttestation(attesting_indices, randomAttestationData(), randomSignature());
  }

  public DepositData randomDepositData() {
    BLSKeyPair keyPair = BLSKeyPair.random(nextSeed());
    BLSPublicKey pubkey = keyPair.getPublicKey();
    Bytes32 withdrawal_credentials = randomBytes32();

    DepositMessage proof_of_possession_data =
        new DepositMessage(
            pubkey, withdrawal_credentials, UnsignedLong.valueOf(Constants.MAX_EFFECTIVE_BALANCE));

    final Bytes domain = compute_domain(DOMAIN_DEPOSIT);
    final Bytes signing_root = compute_signing_root(proof_of_possession_data, domain);

    BLSSignature proof_of_possession = BLS.sign(keyPair.getSecretKey(), signing_root);

    return new DepositData(proof_of_possession_data, proof_of_possession);
  }

  public DepositWithIndex randomDepositWithIndex() {
    return new DepositWithIndex(
        SSZVector.createMutable(32, randomBytes32()),
        randomDepositData(),
        randomUnsignedLong().mod(UnsignedLong.valueOf(Constants.DEPOSIT_CONTRACT_TREE_DEPTH)));
  }

  public Deposit randomDepositWithoutIndex() {
    return new Deposit(
        SSZVector.createMutable(Constants.DEPOSIT_CONTRACT_TREE_DEPTH + 1, randomBytes32()),
        randomDepositData());
  }

  public Deposit randomDeposit() {
    return new Deposit(
        SSZVector.createMutable(Constants.DEPOSIT_CONTRACT_TREE_DEPTH + 1, randomBytes32()),
        randomDepositData());
  }

  public tech.pegasys.teku.pow.event.Deposit randomDepositEvent(UnsignedLong index) {
    return new tech.pegasys.teku.pow.event.Deposit(
        BLSPublicKey.random(nextSeed()),
        randomBytes32(),
        randomSignature(),
        randomUnsignedLong(),
        index);
  }

  public ArrayList<DepositWithIndex> randomDeposits(int num) {
    ArrayList<DepositWithIndex> deposits = new ArrayList<>();

    for (int i = 0; i < num; i++) {
      deposits.add(randomDepositWithIndex());
    }

    return deposits;
  }

  public SignedVoluntaryExit randomSignedVoluntaryExit() {
    return new SignedVoluntaryExit(randomVoluntaryExit(), randomSignature());
  }

  public VoluntaryExit randomVoluntaryExit() {
    return new VoluntaryExit(randomUnsignedLong(), randomUnsignedLong());
  }

  public SSZList<DepositWithIndex> newDeposits(int numDeposits) {
    SSZMutableList<DepositWithIndex> deposits =
        SSZList.createMutable(DepositWithIndex.class, Constants.MAX_DEPOSITS);
    final DepositGenerator depositGenerator = new DepositGenerator();

    for (int i = 0; i < numDeposits; i++) {
      BLSKeyPair keypair = BLSKeyPair.random(i);
      DepositData depositData =
          depositGenerator.createDepositData(
              keypair,
              UnsignedLong.valueOf(Constants.MAX_EFFECTIVE_BALANCE),
              keypair.getPublicKey());

      SSZVector<Bytes32> proof =
          SSZVector.createMutable(Constants.DEPOSIT_CONTRACT_TREE_DEPTH + 1, Bytes32.ZERO);
      DepositWithIndex deposit = new DepositWithIndex(proof, depositData, UnsignedLong.valueOf(i));
      deposits.add(deposit);
    }
    return deposits;
  }

  public Validator randomValidator() {
    return Validator.create(
        randomPublicKey(),
        randomBytes32(),
        UnsignedLong.valueOf(Constants.MAX_EFFECTIVE_BALANCE),
        false,
        Constants.FAR_FUTURE_EPOCH,
        Constants.FAR_FUTURE_EPOCH,
        Constants.FAR_FUTURE_EPOCH,
        Constants.FAR_FUTURE_EPOCH);
  }

  public Fork randomFork() {
    return new Fork(randomBytes4(), randomBytes4(), randomUnsignedLong());
  }

  public ForkInfo randomForkInfo() {
    return new ForkInfo(randomFork(), randomBytes32());
  }

  public EnrForkId randomEnrForkId() {
    return new EnrForkId(randomBytes4(), randomBytes4(), randomUnsignedLong());
  }

  public BeaconState randomBeaconState() {
    return randomBeaconState(100);
  }

  public BeaconState randomBeaconState(final int validatorCount) {
    return BeaconState.create(
        randomUnsignedLong(),
        randomBytes32(),
        randomUnsignedLong(),
        randomFork(),
        randomBeaconBlockHeader(),
        randomSSZVector(Bytes32.ZERO, Constants.SLOTS_PER_HISTORICAL_ROOT, this::randomBytes32),
        randomSSZVector(Bytes32.ZERO, Constants.SLOTS_PER_HISTORICAL_ROOT, this::randomBytes32),
        randomSSZList(Bytes32.class, 100, Constants.HISTORICAL_ROOTS_LIMIT, this::randomBytes32),
        randomEth1Data(),
        randomSSZList(
            Eth1Data.class, EPOCHS_PER_ETH1_VOTING_PERIOD * SLOTS_PER_EPOCH, this::randomEth1Data),
        randomUnsignedLong(),
        randomSSZList(
            Validator.class,
            validatorCount,
            Constants.VALIDATOR_REGISTRY_LIMIT,
            this::randomValidator),
        randomSSZList(
            UnsignedLong.class,
            validatorCount,
            Constants.VALIDATOR_REGISTRY_LIMIT,
            this::randomUnsignedLong),
        randomSSZVector(Bytes32.ZERO, Constants.EPOCHS_PER_HISTORICAL_VECTOR, this::randomBytes32),
        randomSSZVector(
            UnsignedLong.ZERO, Constants.EPOCHS_PER_SLASHINGS_VECTOR, this::randomUnsignedLong),
        randomSSZList(
            PendingAttestation.class,
            100,
            Constants.MAX_ATTESTATIONS * Constants.SLOTS_PER_EPOCH,
            this::randomPendingAttestation),
        randomSSZList(
            PendingAttestation.class,
            100,
            Constants.MAX_ATTESTATIONS * Constants.SLOTS_PER_EPOCH,
            this::randomPendingAttestation),
        randomBitvector(Constants.JUSTIFICATION_BITS_LENGTH),
        randomCheckpoint(),
        randomCheckpoint(),
        randomCheckpoint());
  }

  public BeaconState randomBeaconState(UnsignedLong slot) {
    return randomBeaconState().updated(state -> state.setSlot(slot));
  }
}
