/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.storage.store;

import com.google.common.collect.Sets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteUpdater;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.storage.api.VoteUpdateChannel;

public class StoreVoteUpdater implements VoteUpdater {

  private final Store store;
  private final ReadWriteLock lock;
  private final VoteUpdateChannel voteUpdateChannel;
  private final Map<UInt64, VoteTracker> votes = new HashMap<>();

  StoreVoteUpdater(
      final Store store, final ReadWriteLock lock, final VoteUpdateChannel voteUpdateChannel) {
    this.store = store;
    this.lock = lock;
    this.voteUpdateChannel = voteUpdateChannel;
  }

  @Override
  public VoteTracker getVote(UInt64 validatorIndex) {
    VoteTracker txVote = votes.get(validatorIndex);
    if (txVote != null) {
      return txVote;
    } else {
      VoteTracker storeVote = store.getVote(validatorIndex);
      return storeVote != null ? storeVote : VoteTracker.DEFAULT;
    }
  }

  @Override
  public Set<UInt64> getVotedValidatorIndices() {
    return Sets.union(votes.keySet(), store.getVotedValidatorIndices());
  }

  @Override
  public void putVote(UInt64 validatorIndex, VoteTracker vote) {
    votes.put(validatorIndex, vote);
  }

  @Override
  public Bytes32 applyForkChoiceScoreChanges(
      final Checkpoint finalizedCheckpoint,
      final Checkpoint justifiedCheckpoint,
      final List<UInt64> justifiedCheckpointEffectiveBalances) {

    // Ensure the store lock is taken before entering forkChoiceStrategy. Otherwise it takes the
    // protoArray lock first, and may deadlock when it later needs to get votes which requires the
    // store lock.
    lock.writeLock().lock();
    try {
      return store
          .getForkChoiceStrategy()
          .findHead(
              this, finalizedCheckpoint, justifiedCheckpoint, justifiedCheckpointEffectiveBalances);
    } finally {
      lock.writeLock().unlock();
    }
  }

  @Override
  public void commit() {
    // Votes are applied to the store immediately since the changes to the in-memory ProtoArray
    // can't be rolled back.
    store.votes.putAll(votes);
    voteUpdateChannel.onVotesUpdated(votes);
  }
}
