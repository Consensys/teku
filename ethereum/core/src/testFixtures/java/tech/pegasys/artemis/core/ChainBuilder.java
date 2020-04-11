/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.artemis.core;

import static org.assertj.core.util.Preconditions.checkState;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;

import com.google.common.primitives.UnsignedLong;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.bls.BLSKeyPair;
import tech.pegasys.artemis.bls.BLSSignature;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.artemis.datastructures.operations.DepositData;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.util.DepositGenerator;
import tech.pegasys.artemis.datastructures.util.MockStartBeaconStateGenerator;
import tech.pegasys.artemis.datastructures.util.MockStartDepositGenerator;
import tech.pegasys.artemis.datastructures.util.validator.TestMessageSignerService;
import tech.pegasys.artemis.datastructures.validator.MessageSignerService;
import tech.pegasys.artemis.util.config.Constants;

/** A utility for building small, valid chains of blocks with states for testing */
public class ChainBuilder {

  private final List<BLSKeyPair> validatorKeys;
  private final NavigableMap<UnsignedLong, SignedBlockAndState> blocks = new TreeMap<>();

  private BlockProposalTestUtil blockProposalTestUtil = new BlockProposalTestUtil();

  private ChainBuilder(
      final List<BLSKeyPair> validatorKeys,
      final Map<UnsignedLong, SignedBlockAndState> existingBlocks) {
    this.validatorKeys = validatorKeys;
    blocks.putAll(existingBlocks);
  }

  public static ChainBuilder create(final List<BLSKeyPair> validatorKeys) {
    return new ChainBuilder(validatorKeys, Collections.emptyMap());
  }

  /**
   * Create an independent {@code ChainBuilder} with the same history as the current builder. This
   * independent copy can now create a divergent chain.
   *
   * @return An independent copy of this ChainBuilder
   */
  public ChainBuilder fork() {
    return new ChainBuilder(validatorKeys, blocks);
  }

  public SignedBlockAndState getGenesis() {
    return Optional.ofNullable(blocks.firstEntry()).map(Entry::getValue).orElse(null);
  }

  public SignedBlockAndState getLatestBlock() {
    return Optional.ofNullable(blocks.lastEntry()).map(Entry::getValue).orElse(null);
  }

  public SignedBlockAndState getBlockAtSlot(final long slot) {
    return getBlockAtSlot(UnsignedLong.valueOf(slot));
  }

  public SignedBlockAndState getBlockAtSlot(final UnsignedLong slot) {
    return Optional.ofNullable(blocks.get(slot)).orElse(null);
  }

  public SignedBlockAndState getLatestBlockAtSlot(final long slot) {
    return getLatestBlockAtSlot(UnsignedLong.valueOf(slot));
  }

  public SignedBlockAndState getLatestBlockAtSlot(final UnsignedLong slot) {
    return Optional.ofNullable(blocks.floorEntry(slot)).map(Entry::getValue).orElse(null);
  }

  public SignedBlockAndState getLatestBlockAtEpoch(final long epoch) {
    return getLatestBlockAtEpoch(UnsignedLong.valueOf(epoch));
  }

  public SignedBlockAndState getLatestBlockAtEpoch(final UnsignedLong epoch) {
    final UnsignedLong slot = compute_start_slot_at_epoch(epoch);
    return getLatestBlockAtSlot(slot);
  }

  public SignedBlockAndState generateGenesis() {
    checkState(blocks.isEmpty(), "Genesis already created");
    final UnsignedLong genesisSlot = UnsignedLong.valueOf(Constants.GENESIS_SLOT);

    // Generate genesis state
    final List<DepositData> initialDepositData =
        new MockStartDepositGenerator(new DepositGenerator(true)).createDeposits(validatorKeys);
    final BeaconState genesisState =
        new MockStartBeaconStateGenerator()
            .createInitialBeaconState(UnsignedLong.ZERO, initialDepositData);

    // Generage genesis block
    BeaconBlock genesisBlock = new BeaconBlock(genesisState.hash_tree_root());
    final SignedBeaconBlock signedBlock = new SignedBeaconBlock(genesisBlock, BLSSignature.empty());

    final SignedBlockAndState blockAndState = new SignedBlockAndState(signedBlock, genesisState);
    blocks.put(genesisSlot, blockAndState);
    return blockAndState;
  }

  public void generateBlocksUpToSlot(final long slot) throws StateTransitionException {
    generateBlocksUpToSlot(UnsignedLong.valueOf(slot));
  }

  public void generateBlocksUpToSlot(final UnsignedLong slot) throws StateTransitionException {
    assertBlockCanBeGenerated();

    SignedBlockAndState latestBlock = getLatestBlock();
    while (latestBlock.getState().getSlot().compareTo(slot) < 0) {
      latestBlock = generateNextBlock();
    }
  }

  public SignedBlockAndState generateNextBlock() throws StateTransitionException {
    assertBlockCanBeGenerated();
    return generateNextBlock(0);
  }

  public SignedBlockAndState generateNextBlock(final int skipSlots)
      throws StateTransitionException {
    assertBlockCanBeGenerated();
    final SignedBlockAndState latest = getLatestBlock();
    final UnsignedLong nextSlot =
        latest.getState().getSlot().plus(UnsignedLong.valueOf(1 + skipSlots));
    return generateBlockAtSlot(nextSlot);
  }

  public SignedBlockAndState generateBlockAtSlot(final long slot) throws StateTransitionException {
    return generateBlockAtSlot(UnsignedLong.valueOf(slot));
  }

  public SignedBlockAndState generateBlockAtSlot(final UnsignedLong slot)
      throws StateTransitionException {
    assertBlockCanBeGenerated();
    final SignedBlockAndState latest = getLatestBlock();
    checkState(
        slot.compareTo(latest.getState().getSlot()) > 0,
        "Cannot generate block at historical slot");

    return appendNewBlockToChain(slot);
  }

  private void assertBlockCanBeGenerated() {
    checkState(!blocks.isEmpty(), "Genesis block must be created before blocks can be added.");
  }

  private SignedBlockAndState appendNewBlockToChain(final UnsignedLong slot)
      throws StateTransitionException {
    final SignedBlockAndState latestBlockAndState = getLatestBlock();
    final BeaconState preState = latestBlockAndState.getState();
    final Bytes32 parentRoot = latestBlockAndState.getBlock().getMessage().hash_tree_root();

    final int proposerIndex = blockProposalTestUtil.getProposerIndexForSlot(preState, slot);
    final MessageSignerService signer = getSigner(proposerIndex);
    final SignedBlockAndState nextBlockAndState =
        blockProposalTestUtil.createEmptyBlock(signer, slot, preState, parentRoot);

    blocks.put(slot, nextBlockAndState);
    return nextBlockAndState;
  }

  private MessageSignerService getSigner(final int proposerIndex) {
    return new TestMessageSignerService(validatorKeys.get(proposerIndex));
  }
}
