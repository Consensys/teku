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

package tech.pegasys.teku.util.async;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.util.Waiter.waitFor;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class AsyncRunnerTest {

  @Test
  public void testRecurrentTaskCancel() throws Exception {
    AsyncRunner runner = DelayedExecutorAsyncRunner.create();
    AtomicInteger counter = new AtomicInteger();
    Cancellable task =
        runner.runWithFixedDelay(counter::incrementAndGet, 100, TimeUnit.MILLISECONDS, t -> {});
    waitFor(() -> assertThat(counter).hasValueGreaterThan(3));
    task.cancel();
    int cnt1 = counter.get();
    Thread.sleep(500);
    // 1 task may be completing during the cancel() call
    assertThat(counter).hasValueLessThanOrEqualTo(cnt1 + 1);
  }

  @Test
  public void testRecurrentTaskExceptionHandler() {
    AsyncRunner runner = DelayedExecutorAsyncRunner.create();
    AtomicInteger counter = new AtomicInteger();
    AtomicReference<Throwable> exception = new AtomicReference<>();
    Cancellable task =
        runner.runWithFixedDelay(
            () -> {
              if (counter.incrementAndGet() == 3) {
                throw new RuntimeException("Ups");
              }
            },
            100,
            TimeUnit.MILLISECONDS,
            exception::set);
    waitFor(() -> assertThat(counter).hasValueGreaterThan(3));
    assertThat(exception.get()).hasMessageContaining("Ups");
    task.cancel();
  }
}
