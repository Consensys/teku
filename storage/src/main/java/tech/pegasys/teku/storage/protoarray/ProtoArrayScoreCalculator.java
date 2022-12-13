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

import static com.google.common.base.Preconditions.checkState;
import static java.lang.Math.addExact;
import static java.lang.Math.subtractExact;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteUpdater;

class ProtoArrayScoreCalculator {

  /**
   * Returns a list of `deltas`, where there is one delta for each of the indices in
   * `0..indices.size()`.
   *
   * <p>The deltas are formed by a change between `oldBalances` and `newBalances`, and/or a change
   * of vote in `votes`.
   *
   * <p>## Errors
   *
   * <ul>
   *   <li>If a value in `indices` is greater to or equal to `indices.size()`.
   *   <li>If some `Bytes32` in `votes` is not a key in `indices` (except for `Bytes32.ZERO`, this
   *       is always valid).
   * </ul>
   */
  static LongList computeDeltas(
      VoteUpdater store,
      int protoArraySize,
      Function<Bytes32, Optional<Integer>> getIndexByRoot,
      List<UInt64> oldBalances,
      List<UInt64> newBalances,
      Optional<Bytes32> previousProposerBoostRoot,
      Optional<Bytes32> newProposerBoostRoot,
      UInt64 previousBoostAmount,
      UInt64 newBoostAmount) {
    LongList deltas = new LongArrayList(Collections.nCopies(protoArraySize, 0L));

    UInt64.rangeClosed(UInt64.ZERO, store.getHighestVotedValidatorIndex())
        .forEach(
            validatorIndex ->
                computeDelta(
                    store, getIndexByRoot, oldBalances, newBalances, deltas, validatorIndex));

    previousProposerBoostRoot.ifPresent(
        root -> subtractBalance(getIndexByRoot, deltas, root, previousBoostAmount));
    newProposerBoostRoot.ifPresent(
        root -> addBalance(getIndexByRoot, deltas, root, newBoostAmount));
    return deltas;
  }

  private static void computeDelta(
      final VoteUpdater store,
      final Function<Bytes32, Optional<Integer>> getIndexByRoot,
      final List<UInt64> oldBalances,
      final List<UInt64> newBalances,
      final LongList deltas,
      final UInt64 validatorIndex) {
    VoteTracker vote = store.getVote(validatorIndex);

    // There is no need to create a score change if the validator has never voted
    // or both their votes are for the zero hash (alias to the genesis block).
    if (vote.getCurrentRoot().equals(Bytes32.ZERO) && vote.getNextRoot().equals(Bytes32.ZERO)) {
      return;
    }
    // If vote is already count as equivocated, we don't need to do anything more
    if (vote.isCurrentEquivocating()) {
      return;
    }

    int validatorIndexInt = validatorIndex.intValue();
    // If the validator was not included in the oldBalances (i.e. it did not exist yet)
    // then say its balance was zero.
    UInt64 oldBalance =
        oldBalances.size() > validatorIndexInt ? oldBalances.get(validatorIndexInt) : UInt64.ZERO;

    // If the validator vote is not known in the newBalances, then use a balance of zero.
    // It is possible that there is a vote for an unknown validator if we change our
    // justified state to a new state with a higher epoch that is on a different fork
    // because that may have on-boarded less validators than the prior fork.
    UInt64 newBalance =
        newBalances.size() > validatorIndexInt && !vote.isNextEquivocating()
            ? newBalances.get(validatorIndexInt)
            : UInt64.ZERO;

    if (!vote.getCurrentRoot().equals(vote.getNextRoot()) || !oldBalance.equals(newBalance)) {
      subtractBalance(getIndexByRoot, deltas, vote.getCurrentRoot(), oldBalance);
      addBalance(getIndexByRoot, deltas, vote.getNextRoot(), newBalance);
      final VoteTracker newVote =
          new VoteTracker(
              vote.getNextRoot(),
              vote.getNextRoot(),
              vote.getNextEpoch(),
              vote.isNextEquivocating(),
              vote.isNextEquivocating());
      store.putVote(validatorIndex, newVote);
    }
  }

  private static void addBalance(
      final Function<Bytes32, Optional<Integer>> getIndexByRoot,
      final LongList deltas,
      final Bytes32 targetRoot,
      final UInt64 balanceToAdd) {
    // We ignore the vote if it is not known in `indices`. We assume that it is outside
    // of our tree (i.e. pre-finalization) and therefore not interesting.
    getIndexByRoot
        .apply(targetRoot)
        .ifPresent(
            nextDeltaIndex -> {
              checkState(
                  nextDeltaIndex < deltas.size(), "ProtoArrayForkChoice: Invalid node delta index");
              long delta = addExact(deltas.getLong(nextDeltaIndex), balanceToAdd.longValue());
              deltas.set(nextDeltaIndex.intValue(), delta);
            });
  }

  private static void subtractBalance(
      final Function<Bytes32, Optional<Integer>> getIndexByRoot,
      final LongList deltas,
      final Bytes32 targetRoot,
      final UInt64 balanceToRemove) {

    // We ignore the change if it is not known in `indices`. We assume that it is outside
    // of our tree (i.e. pre-finalization) and therefore not interesting.
    getIndexByRoot
        .apply(targetRoot)
        .ifPresent(
            currentDeltaIndex -> {
              checkState(
                  currentDeltaIndex < deltas.size(),
                  "ProtoArrayForkChoice: Invalid node delta index");
              long delta =
                  subtractExact(deltas.getLong(currentDeltaIndex), balanceToRemove.longValue());
              deltas.set(currentDeltaIndex.intValue(), delta);
            });
  }
}
