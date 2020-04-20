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

package tech.pegasys.artemis.protoarray;

import static com.google.common.primitives.UnsignedLong.ZERO;
import static com.google.common.primitives.UnsignedLong.valueOf;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.artemis.protoarray.ProtoArrayForkChoiceStrategy.computeDeltas;
import static tech.pegasys.artemis.protoarray.ProtoArrayTestUtil.getHash;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ProtoArrayForkChoiceStrategyTest {

  private Map<Bytes32, Integer> indices;
  private ElasticList<VoteTracker> votes;
  private List<UnsignedLong> oldBalances;
  private List<UnsignedLong> newBalances;

  @BeforeEach
  void setUp() {
    indices = new HashMap<>();
    votes = new ElasticList<>(VoteTracker::Default);
    oldBalances = new ArrayList<>();
    newBalances = new ArrayList<>();
  }

  @Test
  void zeroHash() {
    int validatorCount = 16;

    for (int i = 0; i < validatorCount; i++) {
      indices.put(getHash(i), i);
      votes.add(new VoteTracker(Bytes32.ZERO, Bytes32.ZERO, ZERO));
      oldBalances.add(ZERO);
      newBalances.add(ZERO);
    }

    List<Long> deltas = computeDeltas(indices, votes, oldBalances, newBalances);
    assertThat(deltas).hasSize(validatorCount);

    // Deltas should all be zero
    assertThat(deltas).containsOnly(0L);
  }

  @Test
  void allVotedTheSame() {
    final UnsignedLong BALANCE = valueOf(42);
    int validatorCount = 16;

    for (int i = 0; i < validatorCount; i++) {
      indices.put(getHash(i), i);
      votes.add(new VoteTracker(Bytes32.ZERO, getHash(0), ZERO));
      oldBalances.add(BALANCE);
      newBalances.add(BALANCE);
    }

    List<Long> deltas = computeDeltas(indices, votes, oldBalances, newBalances);
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

    votesShouldBeUpdated(votes);
  }

  @Test
  void differentVotes() {
    final UnsignedLong BALANCE = valueOf(42);
    int validatorCount = 16;

    for (int i = 0; i < validatorCount; i++) {
      indices.put(getHash(i), i);
      votes.add(new VoteTracker(Bytes32.ZERO, getHash(i), ZERO));
      oldBalances.add(BALANCE);
      newBalances.add(BALANCE);
    }

    List<Long> deltas = computeDeltas(indices, votes, oldBalances, newBalances);
    assertThat(deltas).hasSize(validatorCount);

    // Each root should have the same delta
    assertThat(deltas).containsOnly(BALANCE.longValue());

    votesShouldBeUpdated(votes);
  }

  @Test
  void movingVotes() {
    final UnsignedLong BALANCE = valueOf(42);
    int validatorCount = 16;

    for (int i = 0; i < validatorCount; i++) {
      indices.put(getHash(i), i);
      votes.add(new VoteTracker(getHash(0), getHash(1), ZERO));
      oldBalances.add(BALANCE);
      newBalances.add(BALANCE);
    }

    List<Long> deltas = computeDeltas(indices, votes, oldBalances, newBalances);

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

    votesShouldBeUpdated(votes);
  }

  @Test
  void moveOutOfTree() {
    final UnsignedLong BALANCE = valueOf(42);

    // There is only one block.
    indices.put(getHash(1), 0);

    // There are two validators.
    oldBalances = Collections.nCopies(2, BALANCE);
    newBalances = Collections.nCopies(2, BALANCE);

    // One validator moves their vote from the block to the zero hash.
    votes.add(new VoteTracker(getHash(1), Bytes32.ZERO, ZERO));

    // One validator moves their vote from the block to something outside the tree.
    votes.add(new VoteTracker(getHash(1), getHash(1337), ZERO));

    List<Long> deltas = computeDeltas(indices, votes, oldBalances, newBalances);
    assertThat(deltas).hasSize(1);

    // The block should have lost both balances
    assertThat(deltas.get(0)).isEqualTo(-BALANCE.longValue() * 2);

    votesShouldBeUpdated(votes);
  }

  @Test
  void changingBalances() {

    final UnsignedLong OLD_BALANCE = valueOf(42);
    final UnsignedLong NEW_BALANCE = OLD_BALANCE.times(valueOf(2));

    int validatorCount = 16;

    for (int i = 0; i < validatorCount; i++) {
      indices.put(getHash(i), i);
      votes.add(new VoteTracker(getHash(0), getHash(1), ZERO));
      oldBalances.add(OLD_BALANCE);
      newBalances.add(NEW_BALANCE);
    }

    List<Long> deltas = computeDeltas(indices, votes, oldBalances, newBalances);
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

    votesShouldBeUpdated(votes);
  }

  @Test
  void validatorAppears() {
    final UnsignedLong BALANCE = valueOf(42);

    // There are two blocks.
    indices.put(getHash(1), 0);
    indices.put(getHash(2), 1);

    // There is only one validator in the old balances.
    oldBalances.add(BALANCE);

    // There are two validators in the new balances.
    newBalances.addAll(List.of(BALANCE, BALANCE));

    // Both validators move votes from block 1 to block 2.
    for (int i = 0; i < 2; i++) {
      votes.add(new VoteTracker(getHash(1), getHash(2), ZERO));
    }

    List<Long> deltas = computeDeltas(indices, votes, oldBalances, newBalances);
    assertThat(deltas).hasSize(2);

    // Block 1 should have only lost one balance
    assertThat(deltas.get(0)).isEqualTo(-BALANCE.longValue());

    // Block 2 should have gained two balances
    assertThat(deltas.get(1)).isEqualTo(2 * BALANCE.longValue());

    votesShouldBeUpdated(votes);
  }

  @Test
  void validatorDisappears() {
    final UnsignedLong BALANCE = valueOf(42);

    // There are two blocks.
    indices.put(getHash(1), 0);
    indices.put(getHash(2), 1);

    // There is only one validator in the old balances.
    oldBalances.addAll(List.of(BALANCE, BALANCE));

    // There are two validators in the new balances.
    newBalances.add(BALANCE);

    // Both validators move votes from block 1 to block 2.
    for (int i = 0; i < 2; i++) {
      votes.add(new VoteTracker(getHash(1), getHash(2), ZERO));
    }

    List<Long> deltas = computeDeltas(indices, votes, oldBalances, newBalances);
    assertThat(deltas).hasSize(2);

    // Block 1 should have lost both balances
    assertThat(deltas.get(0)).isEqualTo(-BALANCE.longValue() * 2);

    // Block 2 should have only gained one balance
    assertThat(deltas.get(1)).isEqualTo(BALANCE.longValue());

    votesShouldBeUpdated(votes);
  }

  private void votesShouldBeUpdated(List<VoteTracker> votes) {
    for (VoteTracker vote : votes) {
      assertThat(vote.getCurrentRoot()).isEqualTo(vote.getNextRoot());
    }
  }
}
