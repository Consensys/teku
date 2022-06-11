/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.storage.protoarray;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.storage.protoarray.ProtoArrayScoreCalculator.computeDeltas;
import static tech.pegasys.teku.storage.protoarray.ProtoArrayTestUtil.createStoreToManipulateVotes;
import static tech.pegasys.teku.storage.protoarray.ProtoArrayTestUtil.getHash;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteUpdater;

public class ProtoArrayScoreCalculatorTest {

  private Object2IntMap<Bytes32> indices = new Object2IntOpenHashMap<>();
  private List<UInt64> oldBalances = new ArrayList<>();
  private List<UInt64> newBalances = new ArrayList<>();
  private Optional<Bytes32> oldProposerBoostRoot = Optional.empty();
  private Optional<Bytes32> newProposerBoostRoot = Optional.empty();
  private UInt64 oldProposerBoostAmount = ZERO;
  private UInt64 newProposerBoostAmount = ZERO;
  private VoteUpdater store = createStoreToManipulateVotes();

  private Optional<Integer> getIndex(final Bytes32 root) {
    return Optional.ofNullable(indices.get(root));
  }

  @Test
  void computeDeltas_zeroHash() {
    int validatorCount = 16;

    for (int i = 0; i < validatorCount; i++) {
      indices.put(getHash(i), i);
      store.getVote(UInt64.valueOf(i));
      oldBalances.add(ZERO);
      newBalances.add(ZERO);
    }

    List<Long> deltas =
        computeDeltas(
            store,
            indices.size(),
            this::getIndex,
            oldBalances,
            newBalances,
            oldProposerBoostRoot,
            newProposerBoostRoot,
            oldProposerBoostAmount,
            newProposerBoostAmount);
    assertThat(deltas).hasSize(validatorCount);

    // Deltas should all be zero
    assertThat(deltas).containsOnly(0L);
  }

  @Test
  void computeDeltas_allVotedTheSame() {
    final UInt64 balance = UInt64.valueOf(42);
    int validatorCount = 16;

    for (int i = 0; i < validatorCount; i++) {
      indices.put(getHash(i), i);
      VoteTracker vote = store.getVote(UInt64.valueOf(i));
      VoteTracker newVote = new VoteTracker(vote.getCurrentRoot(), getHash(0), vote.getNextEpoch());
      store.putVote(UInt64.valueOf(i), newVote);
      oldBalances.add(balance);
      newBalances.add(balance);
    }

    List<Long> deltas =
        computeDeltas(
            store,
            indices.size(),
            this::getIndex,
            oldBalances,
            newBalances,
            oldProposerBoostRoot,
            newProposerBoostRoot,
            oldProposerBoostAmount,
            newProposerBoostAmount);
    assertThat(deltas).hasSize(validatorCount);

    for (int i = 0; i < deltas.size(); i++) {
      long delta = deltas.get(i);
      if (i == 0) {
        // Zero'th root should have a delta
        assertThat(delta).isEqualTo(balance.longValue() * Integer.toUnsignedLong(validatorCount));
      } else {
        // All other deltas should be zero
        assertThat(delta).isEqualTo(0L);
      }
    }

    votesShouldBeUpdated(store);
  }

  @Test
  void computeDeltas_differentVotes() {
    final UInt64 balance = UInt64.valueOf(42);
    int validatorCount = 16;

    for (int i = 0; i < validatorCount; i++) {
      indices.put(getHash(i), i);
      VoteTracker vote = store.getVote(UInt64.valueOf(i));
      VoteTracker newVote = new VoteTracker(vote.getCurrentRoot(), getHash(i), vote.getNextEpoch());
      store.putVote(UInt64.valueOf(i), newVote);
      oldBalances.add(balance);
      newBalances.add(balance);
    }

    List<Long> deltas =
        computeDeltas(
            store,
            indices.size(),
            this::getIndex,
            oldBalances,
            newBalances,
            oldProposerBoostRoot,
            newProposerBoostRoot,
            oldProposerBoostAmount,
            newProposerBoostAmount);
    assertThat(deltas).hasSize(validatorCount);

    // Each root should have the same delta
    assertThat(deltas).containsOnly(balance.longValue());

    votesShouldBeUpdated(store);
  }

  @Test
  void computeDeltas_movingVotes() {
    final UInt64 balance = UInt64.valueOf(42);
    int validatorCount = 16;

    for (int i = 0; i < validatorCount; i++) {
      indices.put(getHash(i), i);
      VoteTracker vote = store.getVote(UInt64.valueOf(i));
      VoteTracker newVote = new VoteTracker(getHash(0), getHash(1), vote.getNextEpoch());
      store.putVote(UInt64.valueOf(i), newVote);
      oldBalances.add(balance);
      newBalances.add(balance);
    }

    List<Long> deltas =
        computeDeltas(
            store,
            indices.size(),
            this::getIndex,
            oldBalances,
            newBalances,
            oldProposerBoostRoot,
            newProposerBoostRoot,
            oldProposerBoostAmount,
            newProposerBoostAmount);

    assertThat(deltas).hasSize(validatorCount);
    long totalDelta = balance.longValue() * Integer.toUnsignedLong(validatorCount);

    for (int i = 0; i < deltas.size(); i++) {
      long delta = deltas.get(i);
      if (i == 0) {
        // Zero'th root should have a negative delta
        assertThat(delta).isEqualTo(-totalDelta);
      } else if (i == 1) {
        // First root should have positive delta
        assertThat(delta).isEqualTo(totalDelta);
      } else {
        // All other deltas should be zero
        assertThat(delta).isEqualTo(0L);
      }
    }

    votesShouldBeUpdated(store);
  }

  @Test
  void computeDeltas_moveOutOfTree() {
    final UInt64 balance = UInt64.valueOf(42);

    // There is only one block.
    indices.put(getHash(1), 0);

    // There are two validators.
    oldBalances = Collections.nCopies(2, balance);
    newBalances = Collections.nCopies(2, balance);

    // One validator moves their vote from the block to the zero hash.
    VoteTracker validator1vote = store.getVote(UInt64.valueOf(0));
    VoteTracker newVote1 = new VoteTracker(getHash(1), Bytes32.ZERO, validator1vote.getNextEpoch());
    store.putVote(UInt64.valueOf(0), newVote1);

    // One validator moves their vote from the block to something outside the tree.
    VoteTracker validator2vote = store.getVote(UInt64.valueOf(1));
    VoteTracker newVote2 =
        new VoteTracker(getHash(1), getHash(1337), validator2vote.getNextEpoch());
    store.putVote(UInt64.valueOf(1), newVote2);

    List<Long> deltas =
        computeDeltas(
            store,
            indices.size(),
            this::getIndex,
            oldBalances,
            newBalances,
            oldProposerBoostRoot,
            newProposerBoostRoot,
            oldProposerBoostAmount,
            newProposerBoostAmount);
    assertThat(deltas).hasSize(1);

    // The block should have lost both balances
    assertThat(deltas.get(0)).isEqualTo(-balance.longValue() * 2);

    votesShouldBeUpdated(store);
  }

  @Test
  void computeDeltas_changingBalances() {

    final UInt64 oldBalance = UInt64.valueOf(42);
    final UInt64 newBalance = oldBalance.times(2);

    int validatorCount = 16;

    for (int i = 0; i < validatorCount; i++) {
      indices.put(getHash(i), i);
      VoteTracker vote = store.getVote(UInt64.valueOf(i));
      VoteTracker newVote = new VoteTracker(getHash(0), getHash(1), vote.getNextEpoch());
      store.putVote(UInt64.valueOf(i), newVote);
      oldBalances.add(oldBalance);
      newBalances.add(newBalance);
    }

    List<Long> deltas =
        computeDeltas(
            store,
            indices.size(),
            this::getIndex,
            oldBalances,
            newBalances,
            oldProposerBoostRoot,
            newProposerBoostRoot,
            oldProposerBoostAmount,
            newProposerBoostAmount);
    assertThat(deltas).hasSize(validatorCount);

    for (int i = 0; i < deltas.size(); i++) {
      long delta = deltas.get(i);
      if (i == 0) {
        // Zero'th root should have a negative delta
        assertThat(delta).isEqualTo(-oldBalance.longValue() * validatorCount);
      } else if (i == 1) {
        // First root should have positive delta
        assertThat(delta).isEqualTo(newBalance.longValue() * validatorCount);
      } else {
        // All other deltas should be zero
        assertThat(delta).isEqualTo(0L);
      }
    }

    votesShouldBeUpdated(store);
  }

  @Test
  void computeDeltas_validatorAppears() {
    final UInt64 balance = UInt64.valueOf(42);

    // There are two blocks.
    indices.put(getHash(1), 0);
    indices.put(getHash(2), 1);

    // There is only one validator in the old balances.
    oldBalances.add(balance);

    // There are two validators in the new balances.
    newBalances.addAll(List.of(balance, balance));

    // Both validators move votes from block 1 to block 2.
    for (int i = 0; i < 2; i++) {
      VoteTracker vote = store.getVote(UInt64.valueOf(i));
      VoteTracker newVote = new VoteTracker(getHash(1), getHash(2), vote.getNextEpoch());
      store.putVote(UInt64.valueOf(i), newVote);
    }

    List<Long> deltas =
        computeDeltas(
            store,
            indices.size(),
            this::getIndex,
            oldBalances,
            newBalances,
            oldProposerBoostRoot,
            newProposerBoostRoot,
            oldProposerBoostAmount,
            newProposerBoostAmount);
    assertThat(deltas).hasSize(2);

    // Block 1 should have only lost one balance
    assertThat(deltas.get(0)).isEqualTo(-balance.longValue());

    // Block 2 should have gained two balances
    assertThat(deltas.get(1)).isEqualTo(2 * balance.longValue());

    votesShouldBeUpdated(store);
  }

  @Test
  void computeDeltas_validatorDisappears() {
    final UInt64 balance = UInt64.valueOf(42);

    // There are two blocks.
    indices.put(getHash(1), 0);
    indices.put(getHash(2), 1);

    // There are two validator in the old balances.
    oldBalances.addAll(List.of(balance, balance));

    // There is only one validator in the new balances.
    newBalances.add(balance);

    // Both validators move votes from block 1 to block 2.
    for (int i = 0; i < 2; i++) {
      VoteTracker vote = store.getVote(UInt64.valueOf(i));
      VoteTracker newVote = new VoteTracker(getHash(1), getHash(2), vote.getNextEpoch());
      store.putVote(UInt64.valueOf(i), newVote);
    }

    List<Long> deltas =
        computeDeltas(
            store,
            indices.size(),
            this::getIndex,
            oldBalances,
            newBalances,
            oldProposerBoostRoot,
            newProposerBoostRoot,
            oldProposerBoostAmount,
            newProposerBoostAmount);
    assertThat(deltas).hasSize(2);

    // Block 1 should have lost both balances
    assertThat(deltas.get(0)).isEqualTo(-balance.longValue() * 2);

    // Block 2 should have only gained one balance
    assertThat(deltas.get(1)).isEqualTo(balance.longValue());

    votesShouldBeUpdated(store);
  }

  @Test
  void computeDeltas_addingProposerBoost() {
    oldProposerBoostAmount = UInt64.valueOf(42);

    // There are two blocks.
    indices.put(getHash(1), 0);
    indices.put(getHash(2), 1);

    // Proposer boost is added to block 2 with a different value
    newProposerBoostRoot = Optional.of(getHash(2));
    newProposerBoostAmount = UInt64.valueOf(23);

    List<Long> deltas =
        computeDeltas(
            store,
            indices.size(),
            this::getIndex,
            oldBalances,
            newBalances,
            oldProposerBoostRoot,
            newProposerBoostRoot,
            oldProposerBoostAmount,
            newProposerBoostAmount);
    assertThat(deltas).hasSize(2);

    // Block 1 should be unchanged
    assertThat(deltas.get(0)).isZero();

    // Block 2 should have new boost amount added
    assertThat(deltas.get(1)).isEqualTo(newProposerBoostAmount.longValue());
  }

  @Test
  void computeDeltas_removingProposerBoost() {
    oldProposerBoostAmount = UInt64.valueOf(42);
    oldProposerBoostRoot = Optional.of(getHash(2));

    // There are two blocks.
    indices.put(getHash(1), 0);
    indices.put(getHash(2), 1);

    // Proposer boost is removed from block 2 with a different value
    newProposerBoostRoot = Optional.empty();
    newProposerBoostAmount = UInt64.valueOf(23);

    List<Long> deltas =
        computeDeltas(
            store,
            indices.size(),
            this::getIndex,
            oldBalances,
            newBalances,
            oldProposerBoostRoot,
            newProposerBoostRoot,
            oldProposerBoostAmount,
            newProposerBoostAmount);
    assertThat(deltas).hasSize(2);

    // Block 1 should be unchanged
    assertThat(deltas.get(0)).isZero();

    // Block 2 should have old boost amount removed
    assertThat(deltas.get(1)).isEqualTo(-oldProposerBoostAmount.longValue());
  }

  @Test
  void computeDeltas_changingProposerBoost() {
    oldProposerBoostAmount = UInt64.valueOf(42);
    oldProposerBoostRoot = Optional.of(getHash(1));

    // There are two blocks.
    indices.put(getHash(1), 0);
    indices.put(getHash(2), 1);

    // Proposer boost is removed from block 2 with a different value
    newProposerBoostRoot = Optional.of(getHash(2));
    newProposerBoostAmount = UInt64.valueOf(23);

    List<Long> deltas =
        computeDeltas(
            store,
            indices.size(),
            this::getIndex,
            oldBalances,
            newBalances,
            oldProposerBoostRoot,
            newProposerBoostRoot,
            oldProposerBoostAmount,
            newProposerBoostAmount);
    assertThat(deltas).hasSize(2);

    // Block 1 should have old boost amount removed
    assertThat(deltas.get(0)).isEqualTo(-oldProposerBoostAmount.longValue());

    // Block 2 should have new boost amount added
    assertThat(deltas.get(1)).isEqualTo(newProposerBoostAmount.longValue());
  }

  @Test
  void computeDeltas_validatorEquivocates() {
    final UInt64 balance = UInt64.valueOf(42);

    // There is one block.
    indices.put(getHash(1), 0);

    // There are two validator in the old balances.
    oldBalances.addAll(List.of(balance, balance));

    // Same two in new balances.
    newBalances.addAll(List.of(balance, balance));

    // Both validators votes for block.
    for (int i = 0; i < 2; i++) {
      VoteTracker vote = store.getVote(UInt64.valueOf(i));
      VoteTracker newVote = new VoteTracker(vote.getCurrentRoot(), getHash(1), vote.getNextEpoch());
      store.putVote(UInt64.valueOf(i), newVote);
    }

    // Validator #0 is marked as equivocated
    VoteTracker vote = store.getVote(ZERO);
    store.putVote(
        ZERO,
        new VoteTracker(vote.getNextRoot(), vote.getNextRoot(), vote.getNextEpoch(), true, true));

    List<Long> deltas =
        computeDeltas(
            store,
            indices.size(),
            this::getIndex,
            oldBalances,
            newBalances,
            oldProposerBoostRoot,
            newProposerBoostRoot,
            oldProposerBoostAmount,
            newProposerBoostAmount);
    assertThat(deltas).hasSize(1);

    // Block should have only one counted vote
    assertThat(deltas.get(0)).isEqualTo(balance.longValue());

    // Votes should be updated
    VoteTracker vote1 = store.getVote(UInt64.ONE);
    assertThat(vote1.getCurrentRoot()).isEqualTo(vote1.getNextRoot());
    // Equivocating validator should be marked
    VoteTracker vote0 = store.getVote(ZERO);
    assertThat(vote0.isCurrentEquivocating()).isTrue();
  }

  @Test
  void computeDeltas_validatorEquivocatesChain() {
    final UInt64 balance = UInt64.valueOf(42);

    // There are 3 blocks
    indices.put(getHash(1), 0);
    indices.put(getHash(2), 1);
    indices.put(getHash(3), 2);

    // There are two validator in the old balances.
    oldBalances.addAll(List.of(balance, balance));

    // Same two in new balances.
    newBalances.addAll(List.of(balance, balance));

    // Both validators moves vote to the last block.
    for (int i = 0; i < 2; i++) {
      VoteTracker vote = store.getVote(UInt64.valueOf(i));
      VoteTracker newVote = new VoteTracker(getHash(2), getHash(3), vote.getNextEpoch());
      store.putVote(UInt64.valueOf(i), newVote);
    }

    // Validator #0 is set to be marked as equivocated
    VoteTracker vote = store.getVote(ZERO);
    store.putVote(ZERO, vote.createNextEquivocating());

    List<Long> deltas =
        computeDeltas(
            store,
            indices.size(),
            this::getIndex,
            oldBalances,
            newBalances,
            oldProposerBoostRoot,
            newProposerBoostRoot,
            oldProposerBoostAmount,
            newProposerBoostAmount);
    assertThat(deltas).hasSize(3);

    assertThat(deltas.get(0)).isEqualTo(0);
    // 2nd block should lose both votes
    assertThat(deltas.get(1)).isEqualTo(-balance.longValue() * 2);
    // Only non-equivocated should be added to 3rd
    assertThat(deltas.get(2)).isEqualTo(balance.longValue());

    // Verify that equivocation affects deltas only once
    List<Long> deltas2 =
        computeDeltas(
            store,
            indices.size(),
            this::getIndex,
            oldBalances,
            newBalances,
            oldProposerBoostRoot,
            newProposerBoostRoot,
            oldProposerBoostAmount,
            newProposerBoostAmount);
    assertThat(deltas2).hasSize(3);

    // No subsequent penalty for equivocation 2nd run
    for (int i = 0; i < 3; i++) {
      assertThat(deltas2.get(i)).isEqualTo(0);
    }
  }

  private void votesShouldBeUpdated(VoteUpdater store) {
    UInt64.rangeClosed(ZERO, store.getHighestVotedValidatorIndex())
        .forEach(
            i -> {
              VoteTracker vote = store.getVote(i);
              assertThat(vote.getCurrentRoot()).isEqualTo(vote.getNextRoot());
            });
  }
}
