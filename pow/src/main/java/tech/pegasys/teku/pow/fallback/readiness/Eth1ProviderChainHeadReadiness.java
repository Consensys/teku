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

package tech.pegasys.teku.pow.fallback.readiness;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import tech.pegasys.teku.pow.Eth1Provider;

public class Eth1ProviderChainHeadReadiness implements Eth1ProviderReadiness {
  // TODO use it to compute best candidate
  @SuppressWarnings("unused")
  private final List<Eth1Provider> eth1Providers;
  // TODO use it to compute best candidate
  @SuppressWarnings("unused")
  private Map<Integer, Long> heads;

  private static final long DEFAULT_RESPONSE_TIME_TOLERANCE = 10;
  private final long responseTimeTolerance;

  public Eth1ProviderChainHeadReadiness(final List<Eth1Provider> eth1Providers) {
    this.eth1Providers = eth1Providers;
    this.heads = new HashMap<>();
    this.responseTimeTolerance = DEFAULT_RESPONSE_TIME_TOLERANCE;
  }

  @Override
  public boolean isReady(final Eth1Provider eth1Provider) {
    try {
      return eth1Provider.getLatestEth1Block().get(responseTimeTolerance, TimeUnit.SECONDS) != null;
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      return false;
    }
  }
}
