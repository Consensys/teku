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

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import tech.pegasys.teku.pow.Eth1Provider;
import tech.pegasys.teku.pow.fallback.readiness.Eth1ProviderReadiness;

public class RoundRobinEth1ProviderSelector implements Eth1ProviderSelector {

  final Eth1ProviderReadiness eth1ProviderReadiness;
  final List<Eth1Provider> candidates;
  private final AtomicReference<Eth1Provider> bestCandidate;
  private static final int DEFAULT_PROVIDER_READINESS_CHECK_INTERVAL_SECONDS = 10;
  private final Iterator<Eth1Provider> circularEth1ProviderIterator;

  public RoundRobinEth1ProviderSelector(
      final List<Eth1Provider> candidates, final Eth1ProviderReadiness eth1ProviderReadiness) {
    this(candidates, eth1ProviderReadiness, DEFAULT_PROVIDER_READINESS_CHECK_INTERVAL_SECONDS);
  }

  public RoundRobinEth1ProviderSelector(
      final List<Eth1Provider> candidates,
      final Eth1ProviderReadiness eth1ProviderReadiness,
      final int providerReadinessCheckIntervalSeconds) {
    assert candidates != null && !candidates.isEmpty();
    this.eth1ProviderReadiness = eth1ProviderReadiness;
    this.candidates = candidates;
    this.circularEth1ProviderIterator = new CircularIterator<>(candidates);
    this.bestCandidate = new AtomicReference<>(circularEth1ProviderIterator.next());

    if (providerReadinessCheckIntervalSeconds > 0) {
      Executors.newScheduledThreadPool(1)
          .scheduleAtFixedRate(
              this::checkIfUpdateNeeded,
              providerReadinessCheckIntervalSeconds,
              providerReadinessCheckIntervalSeconds,
              TimeUnit.SECONDS);
    }
  }

  @Override
  public Eth1Provider bestCandidate() {
    return bestCandidate.get();
  }

  public void checkIfUpdateNeeded() {
    if (!eth1ProviderReadiness.isReady(bestCandidate.get())) {
      updateBestCandidate();
    }
  }

  public void updateBestCandidate() {
    for (int i = 0; i < candidates.size(); i++) {
      final Eth1Provider eth1Provider = circularEth1ProviderIterator.next();
      if (eth1ProviderReadiness.isReady(eth1Provider)) {
        bestCandidate.set(eth1Provider);
        return;
      }
    }
  }
}
