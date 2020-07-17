/*
 * Copyright 2019 ConsenSys AG.
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

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FutureUtil {
  private static final Logger LOG = LogManager.getLogger();

  public static <T> void ignoreFuture(final Future<T> future) {}

  static void runWithFixedDelay(
      AsyncRunner runner,
      ExceptionThrowingRunnable runnable,
      Cancellable task,
      long delayAmount,
      TimeUnit delayUnit,
      Consumer<Throwable> exceptionHandler) {

    runner
        .runAfterDelay(
            () -> {
              if (!task.isCancelled()) {
                try {
                  runnable.run();
                } catch (Throwable throwable) {
                  try {
                    exceptionHandler.accept(throwable);
                  } catch (Exception e) {
                    LOG.warn("Exception in exception handler", e);
                  }
                } finally {
                  runWithFixedDelay(
                      runner, runnable, task, delayAmount, delayUnit, exceptionHandler);
                }
              }
            },
            delayAmount,
            delayUnit)
        .finish(() -> {}, exceptionHandler);
  }

  static Cancellable createCancellable() {
    return new Cancellable() {
      private volatile boolean cancelled;

      @Override
      public void cancel() {
        cancelled = true;
      }

      @Override
      public boolean isCancelled() {
        return cancelled;
      }
    };
  }
}
