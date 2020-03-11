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

package tech.pegasys.artemis.statetransition;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static tech.pegasys.artemis.util.config.Constants.MIN_ATTESTATION_INCLUSION_DELAY;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.validator.MessageSignerService;
import tech.pegasys.artemis.statetransition.blockimport.BlockImportResult;
import tech.pegasys.artemis.statetransition.util.ForkChoiceUtil;
import tech.pegasys.artemis.statetransition.util.StartupUtil;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.Store.Transaction;
import tech.pegasys.artemis.util.SSZTypes.SSZList;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.artemis.util.bls.BLS;
import tech.pegasys.artemis.util.bls.BLSKeyGenerator;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.config.Constants;

public class BeaconChainUtil {

  private final StateTransition stateTransition = new StateTransition();
  private final BlockProposalUtil blockCreator = new BlockProposalUtil(stateTransition);
  private final ChainStorageClient storageClient;
  private final List<BLSKeyPair> validatorKeys;
  private final boolean signDeposits;

  private BeaconChainUtil(
      final List<BLSKeyPair> validatorKeys,
      final ChainStorageClient chainStorageClient,
      boolean signDeposits) {
    this.validatorKeys = validatorKeys;
    this.storageClient = chainStorageClient;
    this.signDeposits = signDeposits;
  }

  public static BeaconChainUtil create(
      final int validatorCount, final ChainStorageClient storageClient) {
    final List<BLSKeyPair> validatorKeys = BLSKeyGenerator.generateKeyPairs(validatorCount);
    return create(storageClient, validatorKeys);
  }

  public static BeaconChainUtil create(
      final ChainStorageClient storageClient, final List<BLSKeyPair> validatorKeys) {
    return create(storageClient, validatorKeys, true);
  }

  public static BeaconChainUtil create(
      final ChainStorageClient storageClient,
      final List<BLSKeyPair> validatorKeys,
      final boolean signDeposits) {
    return new BeaconChainUtil(validatorKeys, storageClient, signDeposits);
  }

  public static void initializeStorage(
      final ChainStorageClient chainStorageClient, final List<BLSKeyPair> validatorKeys) {
    initializeStorage(chainStorageClient, validatorKeys, true);
  }

  public static void initializeStorage(
      final ChainStorageClient chainStorageClient,
      final List<BLSKeyPair> validatorKeys,
      final boolean signDeposits) {
    StartupUtil.setupInitialState(chainStorageClient, 0, null, validatorKeys, signDeposits);
  }

  public void initializeStorage() {
    initializeStorage(storageClient);
  }

  public void initializeStorage(final ChainStorageClient chainStorageClient) {
    initializeStorage(chainStorageClient, validatorKeys, signDeposits);
  }

  public void setSlot(final UnsignedLong currentSlot) {
    if (storageClient.isPreGenesis()) {
      throw new IllegalStateException("Cannot set current slot before genesis");
    }
    final UnsignedLong secPerSlot = UnsignedLong.valueOf(Constants.SECONDS_PER_SLOT);
    final UnsignedLong time = storageClient.getGenesisTime().plus(currentSlot.times(secPerSlot));
    final Transaction tx = storageClient.startStoreTransaction();
    tx.setTime(time);
    tx.commit().join();
  }

  public SignedBeaconBlock createBlockAtSlot(final UnsignedLong slot) throws Exception {
    return createBlockAtSlot(slot, true);
  }

  public SignedBeaconBlock createAndImportBlockAtSlot(final long slot) throws Exception {
    return createAndImportBlockAtSlot(UnsignedLong.valueOf(slot));
  }

  public SignedBeaconBlock createAndImportBlockAtSlot(
      final UnsignedLong slot, List<Attestation> attestations) throws Exception {
    Optional<SSZList<Attestation>> sszList =
        attestations.isEmpty()
            ? Optional.empty()
            : Optional.of(
                SSZList.createMutable(attestations, Constants.MAX_ATTESTATIONS, Attestation.class));

    return createAndImportBlockAtSlot(slot, sszList);
  }

  public SignedBeaconBlock createAndImportBlockAtSlot(
      final UnsignedLong slot, Optional<SSZList<Attestation>> attestations) throws Exception {
    final SignedBeaconBlock block = createBlockAtSlot(slot, true, attestations);
    setSlot(slot);
    final Transaction transaction = storageClient.startStoreTransaction();
    final BlockImportResult importResult =
        ForkChoiceUtil.on_block(transaction, block, stateTransition);
    if (!importResult.isSuccessful()) {
      throw new IllegalStateException(
          "Produced an invalid block ( reason "
              + importResult.getFailureReason().name()
              + ") at slot "
              + slot
              + ": "
              + block);
    }
    final SafeFuture<Void> result = transaction.commit();
    if (!result.isDone() || result.isCompletedExceptionally()) {
      throw new IllegalStateException(
          "Transaction did not commit immediately. Are you using a disk storage backed ChainStorageClient without having storage running?");
    }
    storageClient.updateBestBlock(
        block.getMessage().hash_tree_root(), block.getMessage().getSlot());
    return importResult.getBlock();
  }

  public SignedBeaconBlock createAndImportBlockAtSlot(final UnsignedLong slot) throws Exception {
    return createAndImportBlockAtSlot(slot, Optional.empty());
  }

  public SignedBeaconBlock createBlockAtSlotFromInvalidProposer(final UnsignedLong slot)
      throws Exception {
    return createBlockAtSlot(slot, false);
  }

  private SignedBeaconBlock createBlockAtSlot(final UnsignedLong slot, boolean withValidProposer)
      throws Exception {
    return createBlockAtSlot(slot, withValidProposer, Optional.empty());
  }

  private SignedBeaconBlock createBlockAtSlot(
      final UnsignedLong slot,
      boolean withValidProposer,
      Optional<SSZList<Attestation>> attestations)
      throws Exception {
    checkState(
        withValidProposer || validatorKeys.size() > 1,
        "Must have >1 validator in order to create a block from an invalid proposer.");
    final Bytes32 bestBlockRoot = storageClient.getBestBlockRoot();
    final BeaconBlock bestBlock = storageClient.getStore().getBlock(bestBlockRoot);
    final BeaconState preState = storageClient.getBestBlockRootState();
    checkArgument(bestBlock.getSlot().compareTo(slot) < 0, "Slot must be in the future.");

    final int correctProposerIndex = blockCreator.getProposerIndexForSlot(preState, slot);
    final int proposerIndex =
        withValidProposer ? correctProposerIndex : getWrongProposerIndex(correctProposerIndex);

    final MessageSignerService signer = getSigner(proposerIndex);
    if (attestations.isPresent()) {
      return blockCreator.createBlockWithAttestations(
          signer, slot, preState, bestBlockRoot, attestations.get());
    } else {
      return blockCreator.createEmptyBlock(signer, slot, preState, bestBlockRoot);
    }
  }

  public void finalizeChainAtEpoch(final UnsignedLong epoch) throws Exception {
    if (storageClient.getStore().getFinalizedCheckpoint().getEpoch().compareTo(epoch) >= 0) {
      throw new Exception("Chain already finalized at this or higher epoch");
    }

    AttestationGenerator attestationGenerator = new AttestationGenerator(validatorKeys);
    createAndImportBlockAtSlot(
        storageClient.getBestSlot().plus(UnsignedLong.valueOf(MIN_ATTESTATION_INCLUSION_DELAY)));

    while (storageClient.getStore().getFinalizedCheckpoint().getEpoch().compareTo(epoch) < 0) {

      BeaconState headState =
          storageClient.getStore().getBlockState(storageClient.getBestBlockRoot());
      BeaconBlock headBlock = storageClient.getStore().getBlock(storageClient.getBestBlockRoot());
      UnsignedLong slot = storageClient.getBestSlot();
      SSZList<Attestation> currentSlotAssignments =
          SSZList.createMutable(
              attestationGenerator.getAttestationsForSlot(headState, headBlock, slot),
              Constants.MAX_ATTESTATIONS,
              Attestation.class);
      createAndImportBlockAtSlot(
          storageClient.getBestSlot().plus(UnsignedLong.ONE), Optional.of(currentSlotAssignments));
    }
  }

  public int getWrongProposerIndex(final int actualProposerIndex) {
    return actualProposerIndex == 0 ? 1 : actualProposerIndex - 1;
  }

  private MessageSignerService getSigner(final int proposerIndex) {
    BLSKeyPair proposerKey = validatorKeys.get(proposerIndex);
    return (message) -> SafeFuture.completedFuture(BLS.sign(proposerKey.getSecretKey(), message));
  }
}
