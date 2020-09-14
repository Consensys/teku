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

package tech.pegasys.teku.infrastructure.async;

import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;

public class AsyncRunnerFactory {
  private final Collection<AsyncRunner> asyncRunners = new CopyOnWriteArrayList<>();

  private final MetricTrackingExecutorFactory executorFactory;

  public AsyncRunnerFactory(final MetricTrackingExecutorFactory executorFactory) {
    this.executorFactory = executorFactory;
  }

  public AsyncRunner create(final String name, final int maxThreads) {
    final AsyncRunner asyncRunner =
        ScheduledExecutorAsyncRunner.create(name, maxThreads, executorFactory);
    asyncRunners.add(asyncRunner);
    return asyncRunner;
  }

  public Collection<AsyncRunner> getAsyncRunners() {
    return asyncRunners;
  }
}
