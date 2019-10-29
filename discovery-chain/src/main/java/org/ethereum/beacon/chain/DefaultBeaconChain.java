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

package org.ethereum.beacon.chain;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.beacon.chain.storage.BeaconChainStorage;
import org.ethereum.beacon.chain.storage.BeaconTupleStorage;
import org.ethereum.beacon.consensus.BeaconChainSpec;
import org.ethereum.beacon.consensus.BeaconStateEx;
import org.ethereum.beacon.consensus.BlockTransition;
import org.ethereum.beacon.consensus.transition.EmptySlotTransition;
import org.ethereum.beacon.consensus.verifier.BeaconBlockVerifier;
import org.ethereum.beacon.consensus.verifier.BeaconStateVerifier;
import org.ethereum.beacon.consensus.verifier.VerificationResult;
import org.ethereum.beacon.core.BeaconBlock;
import org.ethereum.beacon.core.BeaconState;
import org.ethereum.beacon.core.types.SlotNumber;
import org.ethereum.beacon.schedulers.Schedulers;
import org.ethereum.beacon.stream.SimpleProcessor;
import org.reactivestreams.Publisher;
import tech.pegasys.artemis.ethereum.core.Hash32;

public class DefaultBeaconChain implements MutableBeaconChain {
  private static final Logger logger = LogManager.getLogger(DefaultBeaconChain.class);

  private final BeaconChainSpec spec;
  private final EmptySlotTransition preBlockTransition;
  private final BlockTransition<BeaconStateEx> blockTransition;
  private final BeaconBlockVerifier blockVerifier;
  private final BeaconStateVerifier stateVerifier;

  private final BeaconChainStorage chainStorage;
  private final BeaconTupleStorage tupleStorage;

  private final SimpleProcessor<BeaconTupleDetails> blockStream;
  private final Schedulers schedulers;

  private BeaconTuple recentlyProcessed;

  public DefaultBeaconChain(
      BeaconChainSpec spec,
      EmptySlotTransition preBlockTransition,
      BlockTransition<BeaconStateEx> blockTransition,
      BeaconBlockVerifier blockVerifier,
      BeaconStateVerifier stateVerifier,
      BeaconChainStorage chainStorage,
      Schedulers schedulers) {
    this.spec = spec;
    this.preBlockTransition = preBlockTransition;
    this.blockTransition = blockTransition;
    this.blockVerifier = blockVerifier;
    this.stateVerifier = stateVerifier;
    this.chainStorage = chainStorage;
    this.tupleStorage = chainStorage.getTupleStorage();
    this.schedulers = schedulers;

    blockStream = new SimpleProcessor<>(schedulers.events(), "DefaultBeaconChain.block");
  }

  @Override
  public void init() {
    if (tupleStorage.isEmpty()) {
      throw new IllegalStateException("Couldn't start from empty storage");
    }
    this.recentlyProcessed = fetchRecentTuple();
    blockStream.onNext(new BeaconTupleDetails(recentlyProcessed));
  }

  private BeaconTuple fetchRecentTuple() {
    SlotNumber maxSlot = chainStorage.getBlockStorage().getMaxSlot();
    List<Hash32> latestBlockRoots = chainStorage.getBlockStorage().getSlotBlocks(maxSlot);
    assert latestBlockRoots.size() > 0;
    return tupleStorage
        .get(latestBlockRoots.get(0))
        .orElseThrow(
            () -> new RuntimeException("Block with stored maxSlot not found, maxSlot: " + maxSlot));
  }

  @Override
  public synchronized ImportResult insert(BeaconBlock block) {
    if (rejectedByTime(block)) {
      return ImportResult.ExpiredBlock;
    }

    if (exist(block)) {
      return ImportResult.ExistingBlock;
    }

    if (!hasParent(block)) {
      return ImportResult.NoParent;
    }

    long s = System.nanoTime();

    BeaconStateEx parentState = pullParentState(block);

    BeaconStateEx preBlockState = preBlockTransition.apply(parentState, block.getSlot());
    VerificationResult blockVerification = blockVerifier.verify(block, preBlockState);
    if (!blockVerification.isPassed()) {
      logger.warn(
          "Block verification failed: "
              + blockVerification
              + ": "
              + block.toString(
                  spec.getConstants(), parentState.getGenesisTime(), spec::signing_root));
      return ImportResult.InvalidBlock;
    }

    BeaconStateEx postBlockState = blockTransition.apply(preBlockState, block);

    VerificationResult stateVerification = stateVerifier.verify(postBlockState, block);
    if (!stateVerification.isPassed()) {
      logger.warn("State verification failed: " + stateVerification);
      return ImportResult.StateMismatch;
    }

    BeaconTuple newTuple = BeaconTuple.of(block, postBlockState);
    tupleStorage.put(newTuple);
    updateFinality(parentState, postBlockState);

    chainStorage.commit();

    long total = System.nanoTime() - s;

    this.recentlyProcessed = newTuple;
    blockStream.onNext(
        new BeaconTupleDetails(block, preBlockState, postBlockState, postBlockState));

    logger.info(
        "new block inserted: {} in {}s",
        newTuple
            .getBlock()
            .toString(
                spec.getConstants(), newTuple.getState().getGenesisTime(), spec::signing_root),
        String.format("%.3f", ((double) total) / 1_000_000_000d));

    return ImportResult.OK;
  }

  @Override
  public BeaconTuple getRecentlyProcessed() {
    return recentlyProcessed;
  }

  private void updateFinality(BeaconState previous, BeaconState current) {
    if (!previous.getFinalizedCheckpoint().equals(current.getFinalizedCheckpoint())) {
      chainStorage.getFinalizedStorage().set(current.getFinalizedCheckpoint());
    }
    if (!previous.getCurrentJustifiedCheckpoint().equals(current.getCurrentJustifiedCheckpoint())) {
      chainStorage.getJustifiedStorage().set(current.getCurrentJustifiedCheckpoint());
    }
  }

  private BeaconStateEx pullParentState(BeaconBlock block) {
    Optional<BeaconTuple> parent = tupleStorage.get(block.getParentRoot());
    checkArgument(parent.isPresent(), "No parent for block %s", block);
    BeaconTuple parentTuple = parent.get();

    return parentTuple.getState();
  }

  private boolean exist(BeaconBlock block) {
    Hash32 blockHash = spec.signing_root(block);
    return chainStorage.getBlockStorage().get(blockHash).isPresent();
  }

  private boolean hasParent(BeaconBlock block) {
    return chainStorage.getBlockStorage().get(block.getParentRoot()).isPresent();
  }

  /**
   * There is no sense in importing block with a slot that is too far in the future.
   *
   * @param block block to run check on.
   * @return true if block should be rejected, false otherwise.
   */
  private boolean rejectedByTime(BeaconBlock block) {
    SlotNumber nextToCurrentSlot =
        spec.get_current_slot(recentlyProcessed.getState(), schedulers.getCurrentTime())
            .increment();

    return block.getSlot().greater(nextToCurrentSlot);
  }

  @Override
  public Publisher<BeaconTupleDetails> getBlockStatesStream() {
    return blockStream;
  }
}
