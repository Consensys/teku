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

import static tech.pegasys.teku.util.async.SafeFuture.propagateResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class StubAsyncRunner implements AsyncRunner {
  private List<Runnable> queuedActions = new ArrayList<>();

  @Override
  public <U> SafeFuture<U> runAsync(final Supplier<SafeFuture<U>> action) {
    final SafeFuture<U> result = new SafeFuture<>();
    queuedActions.add(
        () -> {
          try {
            propagateResult(action.get(), result);
          } catch (final Throwable t) {
            result.completeExceptionally(t);
          }
        });
    return result;
  }

  @Override
  public <U> SafeFuture<U> runAfterDelay(
      Supplier<SafeFuture<U>> action, long delayAmount, TimeUnit delayUnit) {
    return runAsync(action);
  }

  @Override
  public void shutdown() {
    queuedActions.clear();
  }

  public void executeQueuedActions() {
    final List<Runnable> actionsToExecute = queuedActions;
    queuedActions = new ArrayList<>();
    actionsToExecute.forEach(Runnable::run);
  }

  public void executeQueuedActions(int limit) {
    final List<Runnable> actionsToExecute = queuedActions.subList(0, limit);
    final List<Runnable> rest = queuedActions.subList(limit, queuedActions.size());
    queuedActions = new ArrayList<>(rest);
    actionsToExecute.forEach(Runnable::run);
  }

  public void executeUntilDone() {
    executeRepeatedly(1000, true);
  }

  public void executeRepeatedly(final int maxRuns) {
    executeRepeatedly(maxRuns, false);
  }

  public void waitForExactly(final int count) throws InterruptedException {
    int runs = 0;
    int iterations = 0;
    while (runs < count) {
      runs += executeRepeatedly(count - runs, false);
      iterations += 1;
      if (iterations > 100) {
        throw new IllegalStateException("Too many iterations");
      }
      if (runs < count) {
        Thread.sleep(100);
      }
    }
  }

  private int executeRepeatedly(final int maxRuns, final boolean shouldFinish) {
    int iterations = 0;
    for (int i = 0; i < maxRuns; i++) {
      if (countDelayedActions() == 0) {
        return iterations;
      }
      iterations += 1;
      executeQueuedActions();
    }

    if (countDelayedActions() != 0 && shouldFinish) {
      throw new RuntimeException(
          "Unable to execute all delayed actions. "
              + countDelayedActions()
              + " actions remain unexecuted.");
    }
    return iterations;
  }

  public boolean hasDelayedActions() {
    return !queuedActions.isEmpty();
  }

  public int countDelayedActions() {
    return queuedActions.size();
  }
}
