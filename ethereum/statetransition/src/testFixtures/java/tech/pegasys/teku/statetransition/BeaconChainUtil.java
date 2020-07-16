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

package tech.pegasys.teku.statetransition;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static tech.pegasys.teku.util.config.Constants.MIN_ATTESTATION_INCLUSION_DELAY;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.core.AttestationGenerator;
import tech.pegasys.teku.core.BlockProposalTestUtil;
import tech.pegasys.teku.core.StateTransition;
import tech.pegasys.teku.core.results.BlockImportResult;
import tech.pegasys.teku.core.signatures.MessageSignerService;
import tech.pegasys.teku.core.signatures.TestMessageSignerService;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.Deposit;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.util.MockStartValidatorKeyPairFactory;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.util.StartupUtil;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;
import tech.pegasys.teku.util.config.Constants;

public class BeaconChainUtil {

  private final BlockProposalTestUtil blockCreator = new BlockProposalTestUtil();
  private final RecentChainData recentChainData;
  private final ForkChoice forkChoice;
  private final List<BLSKeyPair> validatorKeys;
  private final boolean signDeposits;

  private BeaconChainUtil(
      final List<BLSKeyPair> validatorKeys,
      final RecentChainData recentChainData,
      final ForkChoice forkChoice,
      boolean signDeposits) {
    this.validatorKeys = validatorKeys;
    this.recentChainData = recentChainData;
    this.signDeposits = signDeposits;
    this.forkChoice = forkChoice;
  }

  public static BeaconChainUtil create(
      final int validatorCount, final RecentChainData storageClient) {
    final List<BLSKeyPair> validatorKeys =
        new MockStartValidatorKeyPairFactory().generateKeyPairs(0, validatorCount);
    return create(storageClient, validatorKeys);
  }

  public static BeaconChainUtil create(
      final RecentChainData storageClient, final List<BLSKeyPair> validatorKeys) {
    return create(
        storageClient, validatorKeys, new ForkChoice(storageClient, new StateTransition()), true);
  }

  public static BeaconChainUtil create(
      final RecentChainData storageClient,
      final List<BLSKeyPair> validatorKeys,
      final boolean signDeposits) {
    return new BeaconChainUtil(
        validatorKeys,
        storageClient,
        new ForkChoice(storageClient, new StateTransition()),
        signDeposits);
  }

  public static BeaconChainUtil create(
      final RecentChainData storageClient,
      final List<BLSKeyPair> validatorKeys,
      final ForkChoice forkChoice,
      final boolean signDeposits) {
    return new BeaconChainUtil(validatorKeys, storageClient, forkChoice, signDeposits);
  }

  public static void initializeStorage(
      final RecentChainData recentChainData, final List<BLSKeyPair> validatorKeys) {
    initializeStorage(recentChainData, validatorKeys, true);
  }

  public static void initializeStorage(
      final RecentChainData recentChainData,
      final List<BLSKeyPair> validatorKeys,
      final boolean signDeposits) {
    StartupUtil.setupInitialState(recentChainData, 0, null, validatorKeys, signDeposits);
  }

  public void initializeStorage() {
    initializeStorage(recentChainData);
  }

  public void initializeStorage(final RecentChainData recentChainData) {
    initializeStorage(recentChainData, validatorKeys, signDeposits);
  }

  public void setSlot(final UnsignedLong currentSlot) {
    checkState(!recentChainData.isPreGenesis(), "Cannot set current slot before genesis");
    final UnsignedLong secPerSlot = UnsignedLong.valueOf(Constants.SECONDS_PER_SLOT);
    final UnsignedLong time = recentChainData.getGenesisTime().plus(currentSlot.times(secPerSlot));
    setTime(time);
  }

  public void setTime(final UnsignedLong time) {
    checkState(!recentChainData.isPreGenesis(), "Cannot set time before genesis");
    final StoreTransaction tx = recentChainData.startStoreTransaction();
    tx.setTime(time);
    tx.commit().join();
  }

  public SignedBeaconBlock createBlockAtSlot(final UnsignedLong slot) throws Exception {
    return createBlockAtSlot(slot, true);
  }

  public SignedBeaconBlock createAndImportBlockAtSlot(final long slot) throws Exception {
    return createAndImportBlockAtSlot(UnsignedLong.valueOf(slot));
  }

  public SignedBeaconBlock createAndImportBlockAtSlotWithExits(
      final UnsignedLong slot, List<SignedVoluntaryExit> exits) throws Exception {
    Optional<SSZList<SignedVoluntaryExit>> exitsSSZList =
        exits.isEmpty()
            ? Optional.empty()
            : Optional.of(
                SSZList.createMutable(
                    exits, Constants.MAX_VOLUNTARY_EXITS, SignedVoluntaryExit.class));

    return createAndImportBlockAtSlot(
        slot, Optional.empty(), Optional.empty(), exitsSSZList, Optional.empty());
  }

  public SignedBeaconBlock createAndImportBlockAtSlotWithDeposits(
      final UnsignedLong slot, List<Deposit> deposits) throws Exception {
    Optional<SSZList<Deposit>> depositsSSZlist =
        deposits.isEmpty()
            ? Optional.empty()
            : Optional.of(SSZList.createMutable(deposits, Constants.MAX_DEPOSITS, Deposit.class));

    return createAndImportBlockAtSlot(
        slot, Optional.empty(), depositsSSZlist, Optional.empty(), Optional.empty());
  }

  public SignedBeaconBlock createAndImportBlockAtSlotWithAttestations(
      final UnsignedLong slot, List<Attestation> attestations) throws Exception {
    Optional<SSZList<Attestation>> attestationsSSZList =
        attestations.isEmpty()
            ? Optional.empty()
            : Optional.of(
                SSZList.createMutable(attestations, Constants.MAX_ATTESTATIONS, Attestation.class));

    return createAndImportBlockAtSlot(
        slot, attestationsSSZList, Optional.empty(), Optional.empty(), Optional.empty());
  }

  public SignedBeaconBlock createAndImportBlockAtSlot(
      final UnsignedLong slot,
      Optional<SSZList<Attestation>> attestations,
      Optional<SSZList<Deposit>> deposits,
      Optional<SSZList<SignedVoluntaryExit>> exits,
      Optional<Eth1Data> eth1Data)
      throws Exception {
    final SignedBeaconBlock block =
        createBlockAndStateAtSlot(slot, true, attestations, deposits, exits, eth1Data).getBlock();
    setSlot(slot);
    final Optional<BeaconState> preState = recentChainData.getBlockState(block.getParent_root());
    final BlockImportResult importResult = forkChoice.onBlock(block, preState);
    if (!importResult.isSuccessful()) {
      throw new IllegalStateException(
          "Produced an invalid block ( reason "
              + importResult.getFailureReason().name()
              + ") at slot "
              + slot
              + ": "
              + block);
    }
    forkChoice.processHead(slot);
    return importResult.getBlock();
  }

  public SignedBeaconBlock createAndImportBlockAtSlot(final UnsignedLong slot) throws Exception {
    return createAndImportBlockAtSlot(
        slot, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
  }

  public SignedBeaconBlock createBlockAtSlotFromInvalidProposer(final UnsignedLong slot)
      throws Exception {
    return createBlockAtSlot(slot, false);
  }

  public SignedBeaconBlock createBlockAtSlot(final UnsignedLong slot, boolean withValidProposer)
      throws Exception {
    return createBlockAndStateAtSlot(slot, withValidProposer).getBlock();
  }

  public SignedBlockAndState createBlockAndStateAtSlot(
      final UnsignedLong slot, boolean withValidProposer) throws Exception {
    return createBlockAndStateAtSlot(
        slot,
        withValidProposer,
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }

  private SignedBlockAndState createBlockAndStateAtSlot(
      final UnsignedLong slot,
      boolean withValidProposer,
      Optional<SSZList<Attestation>> attestations,
      Optional<SSZList<Deposit>> deposits,
      Optional<SSZList<SignedVoluntaryExit>> exits,
      Optional<Eth1Data> eth1Data)
      throws Exception {
    checkState(
        withValidProposer || validatorKeys.size() > 1,
        "Must have >1 validator in order to create a block from an invalid proposer.");
    final Bytes32 bestBlockRoot = recentChainData.getBestBlockRoot().orElseThrow();
    final BeaconBlock bestBlock = recentChainData.getStore().getBlock(bestBlockRoot);
    final BeaconState preState = recentChainData.getBestState().orElseThrow();
    checkArgument(bestBlock.getSlot().compareTo(slot) < 0, "Slot must be in the future.");

    final int correctProposerIndex = blockCreator.getProposerIndexForSlot(preState, slot);
    final int proposerIndex =
        withValidProposer ? correctProposerIndex : getWrongProposerIndex(correctProposerIndex);

    final MessageSignerService signer = getSigner(proposerIndex);
    return blockCreator.createBlock(
        signer, slot, preState, bestBlockRoot, attestations, deposits, exits, eth1Data);
  }

  public void finalizeChainAtEpoch(final UnsignedLong epoch) throws Exception {
    if (recentChainData.getStore().getFinalizedCheckpoint().getEpoch().compareTo(epoch) >= 0) {
      throw new Exception("Chain already finalized at this or higher epoch");
    }

    AttestationGenerator attestationGenerator = new AttestationGenerator(validatorKeys);
    createAndImportBlockAtSlot(
        recentChainData.getBestSlot().plus(UnsignedLong.valueOf(MIN_ATTESTATION_INCLUSION_DELAY)));

    while (recentChainData.getStore().getFinalizedCheckpoint().getEpoch().compareTo(epoch) < 0) {

      BeaconState headState =
          recentChainData
              .getStore()
              .getBlockState(recentChainData.getBestBlockRoot().orElseThrow());
      BeaconBlock headBlock =
          recentChainData.getStore().getBlock(recentChainData.getBestBlockRoot().orElseThrow());
      UnsignedLong slot = recentChainData.getBestSlot();
      SSZList<Attestation> currentSlotAssignments =
          SSZList.createMutable(
              attestationGenerator.getAttestationsForSlot(headState, headBlock, slot),
              Constants.MAX_ATTESTATIONS,
              Attestation.class);
      createAndImportBlockAtSlot(
          recentChainData.getBestSlot().plus(UnsignedLong.ONE),
          Optional.of(currentSlotAssignments),
          Optional.empty(),
          Optional.empty(),
          Optional.empty());
    }
  }

  public List<BLSKeyPair> getValidatorKeys() {
    return validatorKeys;
  }

  public int getWrongProposerIndex(final int actualProposerIndex) {
    return actualProposerIndex == 0 ? 1 : actualProposerIndex - 1;
  }

  public MessageSignerService getSigner(final int proposerIndex) {
    return new TestMessageSignerService(validatorKeys.get(proposerIndex));
  }
}
