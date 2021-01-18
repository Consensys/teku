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
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.teku.util.config.Constants.DOMAIN_DEPOSIT;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.LongStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
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
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.datastructures.eth1.Eth1Address;
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
import tech.pegasys.teku.datastructures.state.AnchorPoint;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.state.Fork;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.datastructures.state.PendingAttestation;
import tech.pegasys.teku.datastructures.state.Validator;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.pow.event.DepositsFromBlockEvent;
import tech.pegasys.teku.pow.event.MinGenesisTimeBlockEvent;
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

  public UInt64 randomUInt64() {
    return UInt64.fromLongBits(randomLong());
  }

  public Eth1Address randomEth1Address() {
    return new Eth1Address(randomBytes32().slice(0, 20));
  }

  public Bytes4 randomBytes4() {
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

  public <T> SSZList<T> randomSSZList(
      Class<? extends T> classInfo, long maxSize, Supplier<T> valueGenerator, long numItems) {
    return randomSSZList(classInfo, numItems, maxSize, valueGenerator);
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

  public Bytes48 randomPublicKeyBytes() {
    return pubKeyGenerator.get().toBytesCompressed();
  }

  public Eth1Data randomEth1Data() {
    return new Eth1Data(randomBytes32(), randomUInt64(), randomBytes32());
  }

  /**
   * A random UInt64 that is within a reasonable bound for an epoch number. The maximum value
   * returned won't be reached for another 12,000 years or so.
   */
  public UInt64 randomEpoch() {
    return UInt64.valueOf(new Random(nextSeed()).nextInt(1_000_000_000));
  }

  public SlotAndBlockRoot randomSlotAndBlockRoot() {
    return randomSlotAndBlockRoot(randomUInt64());
  }

  public SlotAndBlockRoot randomSlotAndBlockRoot(final UInt64 slot) {
    return new SlotAndBlockRoot(slot, randomBytes32());
  }

  public Checkpoint randomCheckpoint(final long epoch) {
    return randomCheckpoint(UInt64.valueOf(epoch));
  }

  public Checkpoint randomCheckpoint(final UInt64 epoch) {
    return new Checkpoint(epoch, randomBytes32());
  }

  public Checkpoint randomCheckpoint() {
    return new Checkpoint(randomEpoch(), randomBytes32());
  }

  public AttestationData randomAttestationData() {
    return randomAttestationData(randomUInt64());
  }

  public AttestationData randomAttestationData(final UInt64 slot) {
    return new AttestationData(
        slot, randomUInt64(), randomBytes32(), randomCheckpoint(), randomCheckpoint());
  }

  public Attestation randomAttestation() {
    return new Attestation(randomBitlist(), randomAttestationData(), randomSignature());
  }

  public Attestation randomAttestation(final long slot) {
    return new Attestation(
        randomBitlist(), randomAttestationData(UInt64.valueOf(slot)), randomSignature());
  }

  public AggregateAndProof randomAggregateAndProof() {
    return new AggregateAndProof(randomUInt64(), randomAttestation(), randomSignature());
  }

  public SignedAggregateAndProof randomSignedAggregateAndProof() {
    return new SignedAggregateAndProof(randomAggregateAndProof(), randomSignature());
  }

  public VoteTracker randomVoteTracker() {
    return new VoteTracker(randomBytes32(), randomBytes32(), randomUInt64());
  }

  public PendingAttestation randomPendingAttestation() {
    return new PendingAttestation(
        randomBitlist(), randomAttestationData(), randomUInt64(), randomUInt64());
  }

  public AttesterSlashing randomAttesterSlashing() {
    IndexedAttestation attestation1 = randomIndexedAttestation();
    IndexedAttestation attestation2 =
        new IndexedAttestation(
            attestation1.getAttesting_indices(), randomAttestationData(), randomSignature());
    return new AttesterSlashing(attestation1, attestation2);
  }

  public List<SignedBeaconBlock> randomSignedBeaconBlockSequence(
      final SignedBeaconBlock parent, final int count) {
    return randomSignedBeaconBlockSequence(parent, count, false);
  }

  public List<SignedBeaconBlock> randomSignedBeaconBlockSequence(
      final SignedBeaconBlock parent, final int count, final boolean full) {
    final List<SignedBeaconBlock> blocks = new ArrayList<>();
    SignedBeaconBlock parentBlock = parent;
    for (int i = 0; i < count; i++) {
      final long nextSlot = parentBlock.getSlot().plus(UInt64.ONE).longValue();
      final Bytes32 parentRoot = parentBlock.getRoot();
      final SignedBeaconBlock block = randomSignedBeaconBlock(nextSlot, parentRoot, full);
      blocks.add(block);
      parentBlock = block;
    }
    return blocks;
  }

  public List<SignedBlockAndState> randomSignedBlockAndStateSequence(
      final SignedBeaconBlock parent, final int count, final boolean full) {
    final List<SignedBlockAndState> blocks = new ArrayList<>();
    SignedBeaconBlock parentBlock = parent;
    for (int i = 0; i < count; i++) {
      final long nextSlot = parentBlock.getSlot().plus(UInt64.ONE).longValue();
      final Bytes32 parentRoot = parentBlock.getRoot();
      final BeaconState state = randomBeaconState(UInt64.valueOf(nextSlot));
      final Bytes32 stateRoot = state.hash_tree_root();
      final SignedBeaconBlock block =
          signedBlock(randomBeaconBlock(nextSlot, parentRoot, stateRoot, full));
      blocks.add(new SignedBlockAndState(block, state));
      parentBlock = block;
    }
    return blocks;
  }

  public SignedBeaconBlock randomSignedBeaconBlock(long slotNum) {
    return randomSignedBeaconBlock(UInt64.valueOf(slotNum));
  }

  public SignedBeaconBlock randomSignedBeaconBlock(UInt64 slotNum) {
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

  public SignedBeaconBlock signedBlock(final BeaconBlock block) {
    return new SignedBeaconBlock(block, randomSignature());
  }

  public SignedBeaconBlock randomSignedBeaconBlock(long slotNum, BeaconState state) {
    return randomSignedBeaconBlock(UInt64.valueOf(slotNum), state);
  }

  public SignedBeaconBlock randomSignedBeaconBlock(UInt64 slotNum, BeaconState state) {
    final BeaconBlockBody body = randomBeaconBlockBody();
    final Bytes32 stateRoot = state.hash_tree_root();

    final BeaconBlock block =
        new BeaconBlock(slotNum, randomUInt64(), randomBytes32(), stateRoot, body);
    return new SignedBeaconBlock(block, randomSignature());
  }

  public BeaconBlock randomBeaconBlock(long slotNum) {
    return randomBeaconBlock(UInt64.valueOf(slotNum));
  }

  public BeaconBlock randomBeaconBlock(UInt64 slotNum) {
    final UInt64 proposer_index = randomUInt64();
    Bytes32 previous_root = randomBytes32();
    Bytes32 state_root = randomBytes32();
    BeaconBlockBody body = randomBeaconBlockBody();

    return new BeaconBlock(slotNum, proposer_index, previous_root, state_root, body);
  }

  public SignedBlockAndState randomSignedBlockAndState(final long slot) {
    return randomSignedBlockAndState(UInt64.valueOf(slot));
  }

  public SignedBlockAndState randomSignedBlockAndState(final UInt64 slot) {
    final BeaconBlockAndState blockAndState = randomBlockAndState(slot);

    final SignedBeaconBlock signedBlock =
        new SignedBeaconBlock(blockAndState.getBlock(), randomSignature());
    return new SignedBlockAndState(signedBlock, blockAndState.getState());
  }

  public BeaconBlockAndState randomBlockAndState(final long slot) {
    return randomBlockAndState(UInt64.valueOf(slot));
  }

  public BeaconBlockAndState randomBlockAndState(final UInt64 slot) {
    final BeaconState state = randomBeaconState(slot);
    return randomBlockAndState(slot, state);
  }

  private BeaconBlockAndState randomBlockAndState(final UInt64 slot, final BeaconState state) {
    final Bytes32 parentRoot = randomBytes32();
    final BeaconBlockBody body = randomBeaconBlockBody();
    final UInt64 proposer_index = randomUInt64();
    final BeaconBlockHeader latestHeader =
        new BeaconBlockHeader(
            slot, proposer_index, parentRoot, Bytes32.ZERO, body.hash_tree_root());

    final BeaconState matchingState = state.updated(s -> s.setLatest_block_header(latestHeader));
    final BeaconBlock block =
        new BeaconBlock(slot, proposer_index, parentRoot, matchingState.hashTreeRoot(), body);

    return new BeaconBlockAndState(block, matchingState);
  }

  public BeaconBlock randomBeaconBlock(long slotNum, Bytes32 parentRoot, boolean isFull) {
    return randomBeaconBlock(slotNum, parentRoot, randomBytes32(), isFull);
  }

  public BeaconBlock randomBeaconBlock(
      long slotNum, Bytes32 parentRoot, final Bytes32 stateRoot, boolean isFull) {
    UInt64 slot = UInt64.valueOf(slotNum);

    final UInt64 proposer_index = randomUInt64();
    BeaconBlockBody body = !isFull ? randomBeaconBlockBody() : randomFullBeaconBlockBody();

    return new BeaconBlock(slot, proposer_index, parentRoot, stateRoot, body);
  }

  public BeaconBlock randomBeaconBlock(long slotNum, Bytes32 parentRoot) {
    return randomBeaconBlock(slotNum, parentRoot, false);
  }

  public SignedBeaconBlockHeader randomSignedBeaconBlockHeader() {
    return new SignedBeaconBlockHeader(randomBeaconBlockHeader(), randomSignature());
  }

  public BeaconBlockHeader randomBeaconBlockHeader() {
    return new BeaconBlockHeader(
        randomUInt64(), randomUInt64(), randomBytes32(), randomBytes32(), randomBytes32());
  }

  public BeaconBlockBody randomBeaconBlockBody() {
    return new BeaconBlockBody(
        randomSignature(),
        randomEth1Data(),
        Bytes32.ZERO,
        randomSSZList(
            ProposerSlashing.class,
            Constants.MAX_PROPOSER_SLASHINGS,
            this::randomProposerSlashing,
            1),
        randomSSZList(
            AttesterSlashing.class,
            Constants.MAX_ATTESTER_SLASHINGS,
            this::randomAttesterSlashing,
            1),
        randomSSZList(Attestation.class, Constants.MAX_ATTESTATIONS, this::randomAttestation, 3),
        randomSSZList(Deposit.class, Constants.MAX_DEPOSITS, this::randomDepositWithoutIndex, 1),
        randomSSZList(
            SignedVoluntaryExit.class,
            Constants.MAX_VOLUNTARY_EXITS,
            this::randomSignedVoluntaryExit,
            1));
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
    SSZMutableList<UInt64> attesting_indices =
        SSZList.createMutable(UInt64.class, Constants.MAX_VALIDATORS_PER_COMMITTEE);
    attesting_indices.add(randomUInt64());
    attesting_indices.add(randomUInt64());
    attesting_indices.add(randomUInt64());
    return new IndexedAttestation(attesting_indices, randomAttestationData(), randomSignature());
  }

  public DepositData randomDepositData() {
    BLSKeyPair keyPair = BLSKeyPair.random(nextSeed());
    BLSPublicKey pubkey = keyPair.getPublicKey();
    Bytes32 withdrawal_credentials = randomBytes32();

    DepositMessage proof_of_possession_data =
        new DepositMessage(pubkey, withdrawal_credentials, Constants.MAX_EFFECTIVE_BALANCE);

    final Bytes32 domain = compute_domain(DOMAIN_DEPOSIT);
    final Bytes signing_root = compute_signing_root(proof_of_possession_data, domain);

    BLSSignature proof_of_possession = BLS.sign(keyPair.getSecretKey(), signing_root);

    return new DepositData(proof_of_possession_data, proof_of_possession);
  }

  public DepositWithIndex randomDepositWithIndex() {
    return randomDepositWithIndex(randomLong());
  }

  public DepositWithIndex randomDepositWithIndex(long depositIndex) {
    return new DepositWithIndex(
        SSZVector.createMutable(32, randomBytes32()),
        randomDepositData(),
        UInt64.valueOf(depositIndex));
  }

  public DepositsFromBlockEvent randomDepositsFromBlockEvent(
      final long blockIndex, long depositIndexStartInclusive, long depositIndexEndExclusive) {
    return randomDepositsFromBlockEvent(
        UInt64.valueOf(blockIndex), depositIndexStartInclusive, depositIndexEndExclusive);
  }

  public DepositsFromBlockEvent randomDepositsFromBlockEvent(
      UInt64 blockIndex, long depositIndexStartInclusive, long depositIndexEndExclusive) {
    List<tech.pegasys.teku.pow.event.Deposit> deposits = new ArrayList<>();
    for (long i = depositIndexStartInclusive; i < depositIndexEndExclusive; i++) {
      deposits.add(randomDepositEvent(UInt64.valueOf(i)));
    }
    return DepositsFromBlockEvent.create(
        blockIndex, randomBytes32(), randomUInt64(), deposits.stream());
  }

  public MinGenesisTimeBlockEvent randomMinGenesisTimeBlockEvent(final long blockIndex) {
    return new MinGenesisTimeBlockEvent(
        randomUInt64(), UInt64.valueOf(blockIndex), randomBytes32());
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

  public tech.pegasys.teku.pow.event.Deposit randomDepositEvent(long index) {
    return randomDepositEvent(UInt64.valueOf(index));
  }

  public tech.pegasys.teku.pow.event.Deposit randomDepositEvent(UInt64 index) {
    return new tech.pegasys.teku.pow.event.Deposit(
        BLSPublicKey.random(nextSeed()), randomBytes32(), randomSignature(), randomUInt64(), index);
  }

  public tech.pegasys.teku.pow.event.Deposit randomDepositEvent() {
    return randomDepositEvent(randomUInt64());
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
    return new VoluntaryExit(randomUInt64(), randomUInt64());
  }

  public SSZList<DepositWithIndex> newDeposits(int numDeposits) {
    SSZMutableList<DepositWithIndex> deposits =
        SSZList.createMutable(DepositWithIndex.class, Constants.MAX_DEPOSITS);
    final DepositGenerator depositGenerator = new DepositGenerator();

    for (int i = 0; i < numDeposits; i++) {
      BLSKeyPair keypair = BLSKeyPair.random(i);
      DepositData depositData =
          depositGenerator.createDepositData(
              keypair, Constants.MAX_EFFECTIVE_BALANCE, keypair.getPublicKey());

      SSZVector<Bytes32> proof =
          SSZVector.createMutable(Constants.DEPOSIT_CONTRACT_TREE_DEPTH + 1, Bytes32.ZERO);
      DepositWithIndex deposit = new DepositWithIndex(proof, depositData, UInt64.valueOf(i));
      deposits.add(deposit);
    }
    return deposits;
  }

  public Validator randomValidator() {
    return Validator.create(
        randomPublicKeyBytes(),
        randomBytes32(),
        Constants.MAX_EFFECTIVE_BALANCE,
        false,
        Constants.FAR_FUTURE_EPOCH,
        Constants.FAR_FUTURE_EPOCH,
        Constants.FAR_FUTURE_EPOCH,
        Constants.FAR_FUTURE_EPOCH);
  }

  public Fork randomFork() {
    return new Fork(randomBytes4(), randomBytes4(), randomUInt64());
  }

  public ForkInfo randomForkInfo() {
    return new ForkInfo(randomFork(), randomBytes32());
  }

  public EnrForkId randomEnrForkId() {
    return new EnrForkId(randomBytes4(), randomBytes4(), randomUInt64());
  }

  public BeaconState randomBeaconState() {
    return randomBeaconState(100, 100);
  }

  public BeaconState randomBeaconState(final int validatorCount) {
    return randomBeaconState(validatorCount, 100);
  }

  public BeaconState randomBeaconState(final int validatorCount, final int numItemsInSSZLists) {
    return BeaconStateBuilder.create(this, validatorCount, numItemsInSSZLists).build();
  }

  public BeaconStateBuilder stateBuilder() {
    return BeaconStateBuilder.create(this, 10, 10);
  }

  public BeaconStateBuilder stateBuilder(final int validatorCount, final int numItemsInSSZLists) {
    return BeaconStateBuilder.create(this, validatorCount, numItemsInSSZLists);
  }

  public BeaconState randomBeaconState(UInt64 slot) {
    return randomBeaconState().updated(state -> state.setSlot(slot));
  }

  public AnchorPoint randomAnchorPoint(final long epoch) {
    return randomAnchorPoint(UInt64.valueOf(epoch));
  }

  public AnchorPoint randomAnchorPoint(final UInt64 epoch) {
    final SignedBlockAndState anchorBlockAndState =
        randomSignedBlockAndState(compute_start_slot_at_epoch(epoch));
    return AnchorPoint.fromInitialBlockAndState(anchorBlockAndState);
  }

  public AnchorPoint createAnchorFromState(final BeaconState anchorState) {
    // Create corresponding block
    final BeaconBlock anchorBlock =
        new BeaconBlock(
            anchorState.getSlot(),
            UInt64.ZERO,
            anchorState.getLatest_block_header().getParentRoot(),
            anchorState.hashTreeRoot(),
            new BeaconBlockBody());
    final SignedBeaconBlock signedAnchorBlock =
        new SignedBeaconBlock(anchorBlock, BLSSignature.empty());

    final Bytes32 anchorRoot = anchorBlock.hash_tree_root();
    final UInt64 anchorEpoch = BeaconStateUtil.get_current_epoch(anchorState);
    final Checkpoint anchorCheckpoint = new Checkpoint(anchorEpoch, anchorRoot);

    return AnchorPoint.create(anchorCheckpoint, signedAnchorBlock, anchorState);
  }
}
