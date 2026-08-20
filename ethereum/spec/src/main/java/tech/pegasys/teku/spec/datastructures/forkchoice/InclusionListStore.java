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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import tech.pegasys.teku.infrastructure.collections.LimitedMap;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.InclusionList;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.SignedInclusionList;

public class InclusionListStore {
  private final LimitedMap<SlotAndBlockRoot, Map<UInt64, InclusionListEntry>> inclusionListsByKey;
  private final LimitedMap<SlotAndBlockRoot, Set<UInt64>> equivocatedValidatorIndicesByKey;
  private final ReadWriteLock lock = new ReentrantReadWriteLock();
  private final Lock readLock = lock.readLock();
  private final Lock writeLock = lock.writeLock();

  public InclusionListStore(final int cacheSize) {
    checkArgument(cacheSize > 0, "Cache size must be positive");
    inclusionListsByKey = LimitedMap.createSynchronizedNatural(cacheSize);
    equivocatedValidatorIndicesByKey = LimitedMap.createSynchronizedNatural(cacheSize);
  }

  public void processInclusionList(
      final SignedInclusionList signedInclusionList, final boolean timely) {
    writeLock.lock();
    try {
      final InclusionList inclusionList = signedInclusionList.getMessage();
      final SlotAndBlockRoot key = keyFor(inclusionList);
      final Map<UInt64, InclusionListEntry> inclusionLists =
          inclusionListsByKey.computeIfAbsent(key, __ -> new LinkedHashMap<>());
      final InclusionListEntry storedEntry = inclusionLists.get(inclusionList.getValidatorIndex());
      if (storedEntry != null) {
        if (!storedEntry.signedInclusionList().getMessage().equals(inclusionList)) {
          equivocatedValidatorIndicesByKey
              .computeIfAbsent(key, __ -> new HashSet<>())
              .add(inclusionList.getValidatorIndex());
        }
        return;
      }
      inclusionLists.put(
          inclusionList.getValidatorIndex(), new InclusionListEntry(signedInclusionList, timely));
    } finally {
      writeLock.unlock();
    }
  }

  public Optional<Map<UInt64, InclusionListEntry>> getInclusionLists(final SlotAndBlockRoot key) {
    readLock.lock();
    try {
      return Optional.ofNullable(inclusionListsByKey.get(key)).map(Map::copyOf);
    } finally {
      readLock.unlock();
    }
  }

  public Optional<List<InclusionListEntry>> getInclusionLists(final UInt64 slot) {
    readLock.lock();
    try {
      final List<InclusionListEntry> inclusionLists = new ArrayList<>();
      synchronized (inclusionListsByKey) {
        for (final var entry : inclusionListsByKey.entrySet()) {
          if (entry.getKey().getSlot().equals(slot)) {
            final Set<UInt64> equivocators =
                equivocatedValidatorIndicesByKey.getOrDefault(entry.getKey(), Set.of());
            entry.getValue().entrySet().stream()
                .filter(validatorEntry -> !equivocators.contains(validatorEntry.getKey()))
                .map(Map.Entry::getValue)
                .filter(InclusionListEntry::timely)
                .forEach(inclusionLists::add);
          }
        }
      }
      return Optional.of(List.copyOf(inclusionLists));
    } finally {
      readLock.unlock();
    }
  }

  public boolean isInclusionListEquivocator(
      final SlotAndBlockRoot key, final UInt64 validatorIndex) {
    readLock.lock();
    try {
      return equivocatedValidatorIndicesByKey.getOrDefault(key, Set.of()).contains(validatorIndex);
    } finally {
      readLock.unlock();
    }
  }

  private SlotAndBlockRoot keyFor(final InclusionList inclusionList) {
    return new SlotAndBlockRoot(inclusionList.getSlot(), inclusionList.getDependentRoot());
  }
}
