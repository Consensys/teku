/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.spec.datastructures.forkchoice;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import tech.pegasys.teku.infrastructure.collections.LimitedMap;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.InclusionList;
import tech.pegasys.teku.spec.datastructures.operations.SlotAndInclusionListCommitteeRoot;

public class InclusionListStore {
  private final LimitedMap<SlotAndInclusionListCommitteeRoot, List<InclusionList>>
      inclusionListsByKey;
  private final LimitedMap<SlotAndInclusionListCommitteeRoot, Set<UInt64>>
      equivocatedValidatorIndicesByKey;
  private final ReadWriteLock lock = new ReentrantReadWriteLock();
  private final Lock readLock = lock.readLock();
  private final Lock writeLock = lock.writeLock();

  public InclusionListStore(final int cacheSize) {
    checkArgument(cacheSize > 0, "Cache size must be positive");
    inclusionListsByKey = LimitedMap.createSynchronizedNatural(cacheSize);
    equivocatedValidatorIndicesByKey = LimitedMap.createSynchronizedNatural(cacheSize);
  }

  public void putInclusionList(final InclusionList inclusionList) {
    writeLock.lock();
    try {
      putInclusionListInternal(inclusionList);
    } finally {
      writeLock.unlock();
    }
  }

  public void putEquivocatedInclusionList(final InclusionList inclusionList) {
    writeLock.lock();
    try {
      equivocatedValidatorIndicesByKey
          .computeIfAbsent(keyFor(inclusionList), __ -> new HashSet<>())
          .add(inclusionList.getValidatorIndex());
    } finally {
      writeLock.unlock();
    }
  }

  public Optional<List<InclusionList>> getInclusionLists(
      final SlotAndInclusionListCommitteeRoot key) {
    readLock.lock();
    try {
      return Optional.ofNullable(inclusionListsByKey.get(key)).map(List::copyOf);
    } finally {
      readLock.unlock();
    }
  }

  public Optional<List<InclusionList>> getInclusionLists(final UInt64 slot) {
    readLock.lock();
    try {
      final List<InclusionList> inclusionLists = new ArrayList<>();
      synchronized (inclusionListsByKey) {
        for (final var entry : inclusionListsByKey.entrySet()) {
          if (entry.getKey().slot().equals(slot)) {
            inclusionLists.addAll(entry.getValue());
          }
        }
      }
      return Optional.of(List.copyOf(inclusionLists));
    } finally {
      readLock.unlock();
    }
  }

  public boolean isInclusionListEquivocator(
      final SlotAndInclusionListCommitteeRoot key, final UInt64 validatorIndex) {
    readLock.lock();
    try {
      return equivocatedValidatorIndicesByKey.getOrDefault(key, Set.of()).contains(validatorIndex);
    } finally {
      readLock.unlock();
    }
  }

  private void putInclusionListInternal(final InclusionList inclusionList) {
    inclusionListsByKey
        .computeIfAbsent(keyFor(inclusionList), __ -> new ArrayList<>())
        .add(inclusionList);
  }

  private SlotAndInclusionListCommitteeRoot keyFor(final InclusionList inclusionList) {
    return new SlotAndInclusionListCommitteeRoot(
        inclusionList.getSlot(), inclusionList.getInclusionListCommitteeRoot());
  }
}
