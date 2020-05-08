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

package tech.pegasys.teku.util.async;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class FutureUtil {
  public static <T> void ignoreFuture(final Future<T> future) {}

  static void runWithFixedDelay(
      AsyncRunner runner,
      ExceptionThrowingRunnable runnable,
      SafeFuture<Void> task,
      long delayAmount,
      TimeUnit delayUnit,
      Consumer<Throwable> exceptionHandler) {

    SafeFuture<Void> future =
        runner.runAfterDelay(
            () -> {
              if (!task.isCancelled()) {
                try {
                  runnable.run();
                } catch (Throwable throwable) {
                  if (exceptionHandler != null) {
                    exceptionHandler.accept(throwable);
                  } else {
                    throw throwable;
                  }
                }
                runWithFixedDelay(runner, runnable, task, delayAmount, delayUnit, exceptionHandler);
              }
            },
            delayAmount,
            delayUnit);
    future.propagateExceptionTo(task);
  }
}
