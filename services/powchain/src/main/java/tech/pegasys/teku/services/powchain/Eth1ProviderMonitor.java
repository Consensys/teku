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

package tech.pegasys.teku.services.powchain;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.pow.Eth1ProviderSelector;

public class Eth1ProviderMonitor {
  private final Eth1ProviderSelector eth1ProviderSelector;

  private final AsyncRunner asyncRunner;

  private final AtomicBoolean stopped = new AtomicBoolean(false);

  public Eth1ProviderMonitor(Eth1ProviderSelector eth1ProviderSelector, AsyncRunner asyncRunner) {
    this.eth1ProviderSelector = eth1ProviderSelector;
    this.asyncRunner = asyncRunner;
  }

  public void start() {
    validate();
  }

  public void stop() {
    stopped.set(true);
  }

  private void validate() {
    if (stopped.get()) {
      return;
    }
    eth1ProviderSelector
        .getProviders()
        .parallelStream()
        .forEach(
            monitorableEth1Provider -> {
              if (monitorableEth1Provider.needsToBeValidated()) monitorableEth1Provider.validate();
            });

    asyncRunner.runAfterDelay(this::validate, Duration.ofSeconds(10)).reportExceptions();
  }
}
