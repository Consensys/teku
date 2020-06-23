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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.forkchoice.ForkChoiceState;
import tech.pegasys.teku.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.util.config.Constants;

public class ProtoArrayForkChoiceStrategy implements ForkChoiceState {
  final ReadWriteLock protoArrayLock = new ReentrantReadWriteLock();
  final ReadWriteLock votesLock = new ReentrantReadWriteLock();
  final ReadWriteLock balancesLock = new ReentrantReadWriteLock();
  final ProtoArray protoArray;

  Checkpoint justifiedCheckpoint;
  List<UnsignedLong> balances;
  private Map<UnsignedLong, VoteTracker> votes;

  private ProtoArrayForkChoiceStrategy(
      ProtoArray protoArray,
      Map<UnsignedLong, VoteTracker> votes,
      List<UnsignedLong> balances,
      final Checkpoint justifiedCheckpoint) {
    this.protoArray = protoArray;
    this.votes = votes;
    this.balances = balances;
    this.justifiedCheckpoint = justifiedCheckpoint;
  }

  // Public
  public static ProtoArrayForkChoiceStrategy create(
      Map<UnsignedLong, VoteTracker> votes,
      final Checkpoint finalizedCheckpoint,
      final Checkpoint justifiedCheckpoint) {
    return create(
        votes,
        finalizedCheckpoint,
        justifiedCheckpoint,
        Constants.PROTOARRAY_FORKCHOICE_PRUNE_THRESHOLD);
  }

  private static ProtoArrayForkChoiceStrategy create(
      Map<UnsignedLong, VoteTracker> votes,
      final Checkpoint finalizedCheckpoint,
      final Checkpoint justifiedCheckpoint,
      final int pruningThreshold) {
    ProtoArray protoArray =
        new ProtoArray(
            pruningThreshold,
            justifiedCheckpoint.getEpoch(),
            finalizedCheckpoint.getEpoch(),
            new ArrayList<>(),
            new HashMap<>());

    return new ProtoArrayForkChoiceStrategy(
        protoArray, votes, new ArrayList<>(), justifiedCheckpoint);
  }

  @Override
  public Bytes32 getHead() {
    protoArrayLock.readLock().lock();
    try {
      // justifiedCheckpoint is guarded by justifiedCheckpoint
      return protoArray.findHead(justifiedCheckpoint.getRoot());
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  @VisibleForTesting
  public void setPruneThreshold(int pruneThreshold) {
    protoArrayLock.writeLock().lock();
    try {
      protoArray.setPruneThreshold(pruneThreshold);
    } finally {
      protoArrayLock.writeLock().unlock();
    }
  }

  public int size() {
    protoArrayLock.readLock().lock();
    try {
      return protoArray.getNodes().size();
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  @Override
  public boolean containsBlock(Bytes32 blockRoot) {
    protoArrayLock.readLock().lock();
    try {
      return protoArray.getIndices().containsKey(blockRoot);
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  @Override
  public Optional<UnsignedLong> getBlockSlot(Bytes32 blockRoot) {
    protoArrayLock.readLock().lock();
    try {
      return getProtoNode(blockRoot).map(ProtoNode::getBlockSlot);
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  @Override
  public Optional<Bytes32> getBlockParent(Bytes32 blockRoot) {
    protoArrayLock.readLock().lock();
    try {
      return getProtoNode(blockRoot).map(ProtoNode::getParentRoot);
    } finally {
      protoArrayLock.readLock().unlock();
    }
  }

  public ProtoArrayForkChoiceStrategyUpdater updater() {
    return new ProtoArrayForkChoiceStrategyUpdater(this, votes);
  }

  private Optional<ProtoNode> getProtoNode(Bytes32 blockRoot) {
    return Optional.ofNullable(protoArray.getIndices().get(blockRoot))
        .flatMap(
            blockIndex -> {
              if (blockIndex < protoArray.getNodes().size()) {
                return Optional.of(protoArray.getNodes().get(blockIndex));
              }
              return Optional.empty();
            });
  }
}
