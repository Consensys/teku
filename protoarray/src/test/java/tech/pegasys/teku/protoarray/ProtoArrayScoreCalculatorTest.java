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

package tech.pegasys.teku.protoarray;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.protoarray.ProtoArrayScoreCalculator.computeDeltas;
import static tech.pegasys.teku.protoarray.ProtoArrayTestUtil.createStoreToManipulateVotes;
import static tech.pegasys.teku.protoarray.ProtoArrayTestUtil.getHash;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.forkchoice.MutableStore;
import tech.pegasys.teku.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class ProtoArrayScoreCalculatorTest {

  private Map<Bytes32, Integer> indices;
  private List<UInt64> oldBalances;
  private List<UInt64> newBalances;
  private MutableStore store;

  @BeforeEach
  void setUp() {
    indices = new HashMap<>();
    oldBalances = new ArrayList<>();
    newBalances = new ArrayList<>();
    store = createStoreToManipulateVotes();
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

    List<Long> deltas = computeDeltas(store, indices.size(), indices, oldBalances, newBalances);
    assertThat(deltas).hasSize(validatorCount);

    // Deltas should all be zero
    assertThat(deltas).containsOnly(0L);
  }

  @Test
  void computeDeltas_allVotedTheSame() {
    final UInt64 BALANCE = UInt64.valueOf(42);
    int validatorCount = 16;

    for (int i = 0; i < validatorCount; i++) {
      indices.put(getHash(i), i);
      store.getVote(UInt64.valueOf(i)).setNextRoot(getHash(0));
      oldBalances.add(BALANCE);
      newBalances.add(BALANCE);
    }

    List<Long> deltas = computeDeltas(store, indices.size(), indices, oldBalances, newBalances);
    assertThat(deltas).hasSize(validatorCount);

    for (int i = 0; i < deltas.size(); i++) {
      long delta = deltas.get(i);
      if (i == 0) {
        // Zero'th root should have a delta
        assertThat(delta).isEqualTo(BALANCE.longValue() * Integer.toUnsignedLong(validatorCount));
      } else {
        // All other deltas should be zero
        assertThat(delta).isEqualTo(0L);
      }
    }

    votesShouldBeUpdated(store);
  }

  @Test
  void computeDeltas_differentVotes() {
    final UInt64 BALANCE = UInt64.valueOf(42);
    int validatorCount = 16;

    for (int i = 0; i < validatorCount; i++) {
      indices.put(getHash(i), i);
      store.getVote(UInt64.valueOf(i)).setNextRoot(getHash(i));
      oldBalances.add(BALANCE);
      newBalances.add(BALANCE);
    }

    List<Long> deltas = computeDeltas(store, indices.size(), indices, oldBalances, newBalances);
    assertThat(deltas).hasSize(validatorCount);

    // Each root should have the same delta
    assertThat(deltas).containsOnly(BALANCE.longValue());

    votesShouldBeUpdated(store);
  }

  @Test
  void computeDeltas_movingVotes() {
    final UInt64 BALANCE = UInt64.valueOf(42);
    int validatorCount = 16;

    for (int i = 0; i < validatorCount; i++) {
      indices.put(getHash(i), i);
      VoteTracker vote = store.getVote(UInt64.valueOf(i));
      vote.setCurrentRoot(getHash(0));
      vote.setNextRoot(getHash(1));
      oldBalances.add(BALANCE);
      newBalances.add(BALANCE);
    }

    List<Long> deltas = computeDeltas(store, indices.size(), indices, oldBalances, newBalances);

    assertThat(deltas).hasSize(validatorCount);
    long totalDelta = BALANCE.longValue() * Integer.toUnsignedLong(validatorCount);

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
    final UInt64 BALANCE = UInt64.valueOf(42);

    // There is only one block.
    indices.put(getHash(1), 0);

    // There are two validators.
    oldBalances = Collections.nCopies(2, BALANCE);
    newBalances = Collections.nCopies(2, BALANCE);

    // One validator moves their vote from the block to the zero hash.
    VoteTracker validator1vote = store.getVote(UInt64.valueOf(0));
    validator1vote.setCurrentRoot(getHash(1));
    validator1vote.setNextRoot(Bytes32.ZERO);

    // One validator moves their vote from the block to something outside the tree.
    VoteTracker validator2vote = store.getVote(UInt64.valueOf(1));
    validator2vote.setCurrentRoot(getHash(1));
    validator2vote.setNextRoot(getHash(1337));

    List<Long> deltas = computeDeltas(store, indices.size(), indices, oldBalances, newBalances);
    assertThat(deltas).hasSize(1);

    // The block should have lost both balances
    assertThat(deltas.get(0)).isEqualTo(-BALANCE.longValue() * 2);

    votesShouldBeUpdated(store);
  }

  @Test
  void computeDeltas_changingBalances() {

    final UInt64 OLD_BALANCE = UInt64.valueOf(42);
    final UInt64 NEW_BALANCE = OLD_BALANCE.times(2);

    int validatorCount = 16;

    for (int i = 0; i < validatorCount; i++) {
      indices.put(getHash(i), i);
      VoteTracker vote = store.getVote(UInt64.valueOf(i));
      vote.setCurrentRoot(getHash(0));
      vote.setNextRoot(getHash(1));
      oldBalances.add(OLD_BALANCE);
      newBalances.add(NEW_BALANCE);
    }

    List<Long> deltas = computeDeltas(store, indices.size(), indices, oldBalances, newBalances);
    assertThat(deltas).hasSize(validatorCount);

    for (int i = 0; i < deltas.size(); i++) {
      long delta = deltas.get(i);
      if (i == 0) {
        // Zero'th root should have a negative delta
        assertThat(delta).isEqualTo(-OLD_BALANCE.longValue() * validatorCount);
      } else if (i == 1) {
        // First root should have positive delta
        assertThat(delta).isEqualTo(NEW_BALANCE.longValue() * validatorCount);
      } else {
        // All other deltas should be zero
        assertThat(delta).isEqualTo(0L);
      }
    }

    votesShouldBeUpdated(store);
  }

  @Test
  void computeDeltas_validatorAppears() {
    final UInt64 BALANCE = UInt64.valueOf(42);

    // There are two blocks.
    indices.put(getHash(1), 0);
    indices.put(getHash(2), 1);

    // There is only one validator in the old balances.
    oldBalances.add(BALANCE);

    // There are two validators in the new balances.
    newBalances.addAll(List.of(BALANCE, BALANCE));

    // Both validators move votes from block 1 to block 2.
    for (int i = 0; i < 2; i++) {
      VoteTracker vote = store.getVote(UInt64.valueOf(i));
      vote.setCurrentRoot(getHash(1));
      vote.setNextRoot(getHash(2));
    }

    List<Long> deltas = computeDeltas(store, indices.size(), indices, oldBalances, newBalances);
    assertThat(deltas).hasSize(2);

    // Block 1 should have only lost one balance
    assertThat(deltas.get(0)).isEqualTo(-BALANCE.longValue());

    // Block 2 should have gained two balances
    assertThat(deltas.get(1)).isEqualTo(2 * BALANCE.longValue());

    votesShouldBeUpdated(store);
  }

  @Test
  void computeDeltas_validatorDisappears() {
    final UInt64 BALANCE = UInt64.valueOf(42);

    // There are two blocks.
    indices.put(getHash(1), 0);
    indices.put(getHash(2), 1);

    // There is only one validator in the old balances.
    oldBalances.addAll(List.of(BALANCE, BALANCE));

    // There are two validators in the new balances.
    newBalances.add(BALANCE);

    // Both validators move votes from block 1 to block 2.
    for (int i = 0; i < 2; i++) {
      VoteTracker vote = store.getVote(UInt64.valueOf(i));
      vote.setCurrentRoot(getHash(1));
      vote.setNextRoot(getHash(2));
    }

    List<Long> deltas = computeDeltas(store, indices.size(), indices, oldBalances, newBalances);
    assertThat(deltas).hasSize(2);

    // Block 1 should have lost both balances
    assertThat(deltas.get(0)).isEqualTo(-BALANCE.longValue() * 2);

    // Block 2 should have only gained one balance
    assertThat(deltas.get(1)).isEqualTo(BALANCE.longValue());

    votesShouldBeUpdated(store);
  }

  private void votesShouldBeUpdated(MutableStore store) {
    for (UInt64 i : store.getVotedValidatorIndices()) {
      VoteTracker vote = store.getVote(i);
      assertThat(vote.getCurrentRoot()).isEqualTo(vote.getNextRoot());
    }
  }
}
