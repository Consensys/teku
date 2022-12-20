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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AsyncRunnerFactoryTest {

  private AsyncRunnerFactory asyncRunnerFactory =
      new AsyncRunnerFactory() {
        @Override
        public AsyncRunner create(
            String name, int maxThreads, int maxQueueSize, int threadPriority) {
          return null;
        }

        @Override
        public void shutdown() {}
      };

  @ParameterizedTest
  @ValueSource(strings = {"correctname", "correctNAME", "correct_name", "correct_name__"})
  public void mustAcceptValidMetricNames(String asyncRunnerName) {
    asyncRunnerFactory.validateAsyncRunnerName(asyncRunnerName);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "_incorrectname",
        ":incorrectname",
        "incorrect_name::123",
        "1incorrect_name",
        "$incorrect_name",
        "incorrect-name"
      })
  public void mustRejectInvalidMetricNames(String asyncRunnerName) {
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> asyncRunnerFactory.validateAsyncRunnerName(asyncRunnerName));
  }
}
