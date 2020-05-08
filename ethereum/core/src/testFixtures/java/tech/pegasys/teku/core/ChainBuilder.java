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

package tech.pegasys.teku.core;

import static org.assertj.core.util.Preconditions.checkState;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.teku.util.config.Constants.SLOTS_PER_EPOCH;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.Objects;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.core.signatures.MessageSignerService;
import tech.pegasys.teku.core.signatures.TestMessageSignerService;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockBodyLists;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.DepositData;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.util.DepositGenerator;
import tech.pegasys.teku.datastructures.util.MockStartBeaconStateGenerator;
import tech.pegasys.teku.datastructures.util.MockStartDepositGenerator;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.ssz.SSZTypes.SSZMutableList;
import tech.pegasys.teku.util.config.Constants;

/** A utility for generating small, valid chains of blocks with states for testing */
public class ChainBuilder extends DelegatingEth2Chain {

  private final List<BLSKeyPair> validatorKeys;
  private final AttestationGenerator attestationGenerator;

  private BlockProposalTestUtil blockProposalTestUtil = new BlockProposalTestUtil();

  private ChainBuilder(final List<BLSKeyPair> validatorKeys, final Eth2Chain existingChain) {
    super(new SimpleEth2Chain());
    this.validatorKeys = validatorKeys;

    attestationGenerator = new AttestationGenerator(validatorKeys);
    eth2Chain.putAll(existingChain);
  }

  public static ChainBuilder create(final List<BLSKeyPair> validatorKeys) {
    return new ChainBuilder(validatorKeys, new SimpleEth2Chain());
  }

  /**
   * Create an independent {@code ChainBuilder} with the same history as the current builder. This
   * independent copy can now create a divergent chain.
   *
   * @return An independent copy of this ChainBuilder
   */
  public ChainBuilder fork() {
    return new ChainBuilder(validatorKeys, eth2Chain);
  }

  public SignedBlockAndState generateGenesis() {
    checkState(eth2Chain.isEmpty(), "Genesis already created");
    final UnsignedLong genesisSlot = UnsignedLong.valueOf(Constants.GENESIS_SLOT);

    // Generate genesis state
    final List<DepositData> initialDepositData =
        new MockStartDepositGenerator(new DepositGenerator(true)).createDeposits(validatorKeys);
    final BeaconState genesisState =
        new MockStartBeaconStateGenerator()
            .createInitialBeaconState(genesisSlot, initialDepositData);

    // Generate genesis block
    BeaconBlock genesisBlock = new BeaconBlock(genesisState.hash_tree_root());
    final SignedBeaconBlock signedBlock = new SignedBeaconBlock(genesisBlock, BLSSignature.empty());

    final SignedBlockAndState blockAndState = new SignedBlockAndState(signedBlock, genesisState);
    eth2Chain.putBlockAndState(blockAndState);
    return blockAndState;
  }

  public void generateBlocksUpToSlot(final long slot) throws StateTransitionException {
    generateBlocksUpToSlot(UnsignedLong.valueOf(slot));
  }

  public void generateBlocksUpToSlot(final UnsignedLong slot) throws StateTransitionException {
    assertBlockCanBeGenerated();

    SignedBlockAndState latestBlock = getLatestBlockAndState();
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
    final SignedBlockAndState latest = getLatestBlockAndState();
    final UnsignedLong nextSlot =
        latest.getState().getSlot().plus(UnsignedLong.valueOf(1 + skipSlots));
    return generateBlockAtSlot(nextSlot);
  }

  public SignedBlockAndState generateBlockAtSlot(final long slot) throws StateTransitionException {
    return generateBlockAtSlot(UnsignedLong.valueOf(slot));
  }

  public SignedBlockAndState generateBlockAtSlot(final UnsignedLong slot)
      throws StateTransitionException {
    return generateBlockAtSlot(slot, BlockOptions.create());
  }

  public SignedBlockAndState generateBlockAtSlot(
      final UnsignedLong slot, final BlockOptions options) throws StateTransitionException {
    assertBlockCanBeGenerated();
    final SignedBlockAndState latest = getLatestBlockAndState();
    checkState(
        slot.compareTo(latest.getState().getSlot()) > 0,
        "Cannot generate block at historical slot");

    return appendNewBlockToChain(slot, options);
  }

  /**
   * Utility for streaming valid attestations available for inclusion at the given slot. This
   * utility can be used to assign valid attestations to a generated block.
   *
   * @param slot The slot at which attestations are to be included
   * @return A stream of valid attestations that can be included in a block generated at the given
   *     slot
   */
  public Stream<Attestation> streamValidAttestationsForBlockAtSlot(final UnsignedLong slot) {
    // Calculate bounds for valid head blocks
    final UnsignedLong currentEpoch = compute_epoch_at_slot(slot);
    final UnsignedLong prevEpoch =
        currentEpoch.compareTo(UnsignedLong.ZERO) == 0
            ? currentEpoch
            : currentEpoch.minus(UnsignedLong.ONE);
    final UnsignedLong minBlockSlot = compute_start_slot_at_epoch(prevEpoch);

    // Calculate valid assigned slots to be included in a block at the given slot
    final UnsignedLong slotsPerEpoch = UnsignedLong.valueOf(SLOTS_PER_EPOCH);
    final UnsignedLong minAssignedSlot =
        slot.compareTo(slotsPerEpoch) <= 0 ? UnsignedLong.ZERO : slot.minus(slotsPerEpoch);
    final UnsignedLong minInclusionDiff =
        UnsignedLong.valueOf(Constants.MIN_ATTESTATION_INCLUSION_DELAY);
    final UnsignedLong maxAssignedSlot =
        slot.compareTo(minInclusionDiff) <= 0 ? slot : slot.minus(minInclusionDiff);

    // Generate stream of consistent, valid attestations for inclusion
    return LongStream.rangeClosed(minAssignedSlot.longValue(), maxAssignedSlot.longValue())
        .mapToObj(UnsignedLong::valueOf)
        .map(this::getLatestBlockAndStateAtSlot)
        .filter(Objects::nonNull)
        .filter(b -> b.getSlot().compareTo(minBlockSlot) >= 0)
        .map(SignedBlockAndState::toUnsigned)
        .flatMap(head -> attestationGenerator.streamAttestations(head, head.getSlot()));
  }

  private void assertBlockCanBeGenerated() {
    checkState(!eth2Chain.isEmpty(), "Genesis block must be created before blocks can be added.");
  }

  private SignedBlockAndState appendNewBlockToChain(
      final UnsignedLong slot, final BlockOptions options) throws StateTransitionException {
    final SignedBlockAndState latestBlockAndState = getLatestBlockAndState();
    final BeaconState preState = latestBlockAndState.getState();
    final Bytes32 parentRoot = latestBlockAndState.getBlock().getMessage().hash_tree_root();

    final int proposerIndex = blockProposalTestUtil.getProposerIndexForSlot(preState, slot);
    final MessageSignerService signer = getSigner(proposerIndex);
    final SignedBlockAndState nextBlockAndState =
        blockProposalTestUtil.createBlockWithAttestations(
            signer, slot, preState, parentRoot, options.getAttestations());

    eth2Chain.putBlockAndState(nextBlockAndState);
    return nextBlockAndState;
  }

  private MessageSignerService getSigner(final int proposerIndex) {
    return new TestMessageSignerService(validatorKeys.get(proposerIndex));
  }

  public static final class BlockOptions {
    private SSZMutableList<Attestation> attestations = BeaconBlockBodyLists.createAttestations();

    private BlockOptions() {}

    public static BlockOptions create() {
      return new BlockOptions();
    }

    public BlockOptions addAttestation(final Attestation attestation) {
      attestations.add(attestation);
      return this;
    }

    private SSZList<Attestation> getAttestations() {
      return attestations;
    }
  }
}
