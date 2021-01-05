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

package tech.pegasys.teku.pow.fallback.strategy;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import tech.pegasys.teku.pow.Eth1Provider;

public class PriorityEth1ProviderSelector implements Eth1ProviderSelector {

  final List<Eth1Provider> candidates;
  private final AtomicReference<Eth1Provider> bestCandidate;
  private final AtomicInteger bestCandidateIndex;

  @SuppressWarnings("FutureReturnValueIgnored")
  public PriorityEth1ProviderSelector(final List<Eth1Provider> candidates) {
    assert candidates != null && !candidates.isEmpty();
    this.candidates = candidates;
    this.bestCandidateIndex = new AtomicInteger(0);
    this.bestCandidate = new AtomicReference<>(this.candidates.get(this.bestCandidateIndex.get()));
  }

  @Override
  public Eth1Provider bestCandidate() {
    return bestCandidate.get();
  }

  @Override
  public void updateBestCandidate() {
    int nextIndex = (bestCandidateIndex.get() + 1) % candidates.size();
    bestCandidateIndex.set(nextIndex);
    bestCandidate.set(candidates.get(bestCandidateIndex.get()));
  }

  @Override
  public int candidateCount() {
    return candidates.size();
  }
}
