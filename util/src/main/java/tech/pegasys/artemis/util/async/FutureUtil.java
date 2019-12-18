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

package tech.pegasys.artemis.util.async;

import java.util.concurrent.CompletableFuture;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FutureUtil {
  private static final Logger LOG = LogManager.getLogger();
  /**
   * Always perform an action after a {@link CompletableFuture} is done, regardless of whether or
   * not it fails. The original result or exception of the future is preserved unless the action
   * itself throws an exception, in which case the exception thrown by the action becomes the
   * result.
   *
   * <p>This is equivalent to a finally section of a try/catch block.
   *
   * @param future the future to perform an action after
   * @param action the action to perform
   * @param <T> the result type of the future
   * @return a new future which completes after the supplied future and action have completed,
   *     giving the same result or exception as the original future.
   */
  public static <T> CompletableFuture<T> asyncFinally(
      final CompletableFuture<T> future, final Runnable action) {
    return future.handle(
        (result, error) -> {
          action.run();
          if (error != null) {
            throwUnchecked(error);
          }
          return result;
        });
  }

  /**
   * Log any exceptions resulting from the supplied future and do nothing on successful results.
   *
   * @param level the log level to use
   * @param message the log message
   * @param future the future
   */
  public static void logErrors(
      final Level level, final String message, final CompletableFuture<?> future) {
    future.exceptionally(
        error -> {
          LOG.log(level, message, error);
          return null;
        });
  }

  /**
   * Uses the fact that java generic types are erased to re-throw a Throwable instance without
   * having to declare it. While generally inadvisable, this allows us to re-throw the exact
   * exception a CompletableFuture gave us without ever having to wrap in a new RuntimeException.
   * Since CompletableFuture is expected to work with Throwables it doesn't matter if it's a checked
   * exception or not.
   *
   * @param toThrow the Throwable to throw
   * @param <T> type being used to fool the compiler
   * @throws T toThrow unmodified.
   */
  @SuppressWarnings("unchecked")
  private static <T extends Throwable> void throwUnchecked(Throwable toThrow) throws T {
    // Since the type is erased, this cast doesn't actually do anything and we can throw anything.
    throw (T) toThrow;
  }
}
