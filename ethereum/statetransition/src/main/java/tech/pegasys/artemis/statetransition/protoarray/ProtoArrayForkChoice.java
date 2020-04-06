package tech.pegasys.artemis.statetransition.protoarray;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static tech.pegasys.artemis.util.config.Constants.PROTOARRAY_FORKCHOICE_PRUNE_THRESHOLD;

public class ProtoArrayForkChoice {
  private final ReadWriteLock protoArrayLock = new ReentrantReadWriteLock();
  private final ReadWriteLock votesLock = new ReentrantReadWriteLock();
  private final ReadWriteLock balancesLock = new ReentrantReadWriteLock();
  private final ProtoArray protoArray;
  private final List<VoteTracker> votes;
  private final List<UnsignedLong> balances;

  private ProtoArrayForkChoice(ProtoArray protoArray,
                              List<VoteTracker> votes,
                              List<UnsignedLong> balances) {
    this.protoArray = protoArray;
    this.votes = votes;
    this.balances = balances;
  }

  public static ProtoArrayForkChoice create(UnsignedLong finalizedBlockSlot,
                                            Bytes32 finalizedBlockStateRoot,
                                            UnsignedLong justifiedEpoch,
                                            UnsignedLong finalizedEpoch,
                                            Bytes32 finalizedRoot) {
    ProtoArray protoArray = new ProtoArray(
            PROTOARRAY_FORKCHOICE_PRUNE_THRESHOLD,
            justifiedEpoch,
            finalizedEpoch,
            new ArrayList<>(),
            new HashMap<>()
    );

    protoArray.onBlock(
            finalizedBlockSlot,
            finalizedRoot,
            Optional.empty(),
            finalizedBlockStateRoot,
            justifiedEpoch,
            finalizedEpoch);

    return new ProtoArrayForkChoice(
            protoArray,
            new ArrayList<>(),
            new ArrayList<>());
  }

  public void processAttestation(int validatorIndex,
                                 Bytes32 blockRoot,
                                 UnsignedLong targetEpoch) {
    votesLock.writeLock().lock();
    try {
      VoteTracker vote = votes.get(validatorIndex);

      if (targetEpoch.compareTo(vote.getNextEpoch()) > 0 || vote.equals(VoteTracker.DEFAULT)) {
        vote.setNextRoot(blockRoot);
        vote.setNextEpoch(targetEpoch);
      }
    } finally {
      votesLock.writeLock().unlock();
    }
  }

  public void processBlock(UnsignedLong slot,
                           Bytes32 blockRoot,
                           Bytes32 parentRoot,
                           Bytes32 stateRoot,
                           UnsignedLong justifiedEpoch,
                           UnsignedLong finalizedEpoch) {
    protoArrayLock.writeLock().lock();
    try {
      protoArray.onBlock(
              slot,
              blockRoot,
              Optional.of(parentRoot),
              stateRoot,
              justifiedEpoch,
              finalizedEpoch
      );
    } finally {
      protoArrayLock.writeLock().unlock();
    }
  }

  public Bytes32 findHead(UnsignedLong justifiedEpoch,
                          Bytes32 justifiedRoot,
                          UnsignedLong finalizedEpoch,
                          List<UnsignedLong> justifiedStateBalances) {
    protoArrayLock.writeLock().lock();
    votesLock.writeLock().lock();
    balancesLock.writeLock().lock();
    try {
    } finally {
    }
  }


  // Returns a list of `deltas`, where there is one delta for each of the indices in
  // `0..indices.size()`.
  //
  // The deltas are formed by a change between `oldBalances` and `newBalances`,
  //  and/or a change of vote in `votes`.
  //
  // ## Errors
  //
  // - If a value in `indices` is greater to or equal to `indices.size()`.
  // - If some `Bytes32` in `votes` is not a key in `indices` (except for `Bytes32.ZERO`, this is
  // always valid).
  private List<Long> computeDeltas(Map<Bytes32, Integer> indices,
                                   ElasticList<VoteTracker> votes,
                                   List<UnsignedLong> oldBalances,
                                   List<UnsignedLong> newBalances) {
    List<Long> deltas = new ArrayList<>(Collections.nCopies(indices.size(), 0L));

    for (int validatorIndex = 0; validatorIndex < votes.size(); validatorIndex++) {
      VoteTracker vote = votes.get(validatorIndex);

      // There is no need to create a score change if the validator has never voted
      // or both their votes are for the zero hash (alias to the genesis block).
      if (vote.getCurrentRoot().equals(Bytes32.ZERO) && vote.getNextRoot().equals(Bytes32.ZERO)) {
        continue;
      }

      // If the validator was not included in the oldBalances (i.e. it did not exist yet)
      // then say its balance was zero.
      UnsignedLong oldBalance = oldBalances.size() > validatorIndex ?
              oldBalances.get(validatorIndex) : UnsignedLong.ZERO;

      // If the validator vote is not known in the newBalances, then use a balance of zero.
      //
      // It is possible that there is a vote for an unknown validator if we change our
      // justified state to a new state with a higher epoch that is on a different fork
      // because that may have on-boarded less validators than the prior fork.
      UnsignedLong newBalance = newBalances.size() > validatorIndex ?
              newBalances.get(validatorIndex) : UnsignedLong.ZERO;

      if (!vote.getCurrentRoot().equals(vote.getNextRoot()) || !oldBalance.equals(newBalance)) {
        // We ignore the vote if it is not known in `indices`. We assume that it is outside
        // of our tree (i.e. pre-finalization) and therefore not interesting.
        Integer currentDeltaIndex = indices.(vote.getCurrentRoot());
        if (currentDeltaIndex != null) {
          Long delta = deltas.get(currentDeltaIndex);
          if (delta != null) {

          }
        }

      }

    }
  }
}
