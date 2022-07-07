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

package tech.pegasys.teku.infrastructure.async;

import java.util.Collection;

public interface AsyncRunnerFactory {

  int DEFAULT_MAX_QUEUE_SIZE = 5000;

  default AsyncRunner create(String name, int maxThreads) {
    return create(name, maxThreads, DEFAULT_MAX_QUEUE_SIZE);
  }

  AsyncRunner create(String name, int maxThreads, int maxQueueSize);

  Collection<AsyncRunner> getAsyncRunners();

  static DefaultAsyncRunnerFactory createDefault(
      final MetricTrackingExecutorFactory executorFactory) {
    return new DefaultAsyncRunnerFactory(executorFactory);
  }
}
