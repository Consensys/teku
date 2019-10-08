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

package tech.pegasys.artemis;

import static com.google.common.base.Preconditions.checkArgument;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.eventbus.SubscriberExceptionContext;
import com.google.common.eventbus.SubscriberExceptionHandler;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.util.alogger.ALogger;

class EventBusExceptionHandlerTest {

  private static ExecutorService executor;

  private EventBus bus;

  private final CompletableFuture<Level> logLevelFuture = new CompletableFuture<>();
  private final CompletableFuture<Throwable> exceptionFuture = new CompletableFuture<>();

  @BeforeAll
  static void setupExecutor() {
    executor = Executors.newCachedThreadPool();
  }

  @AfterAll
  static void closeExecutor() {
    executor.shutdown();
  }

  @BeforeEach
  void setupBus() {

    final var recordingLogger =
        new ALogger() {
          @Override
          public void log(final Level level, final String message) {
            logLevelFuture.complete(level);
          }
        };

    final var exceptionHandlerRecordingWrapper =
        new SubscriberExceptionHandler() {
          private final SubscriberExceptionHandler delegate =
              new EventBusExceptionHandler(recordingLogger);

          @Override
          public void handleException(
              final Throwable exception, final SubscriberExceptionContext context) {
            try {
              delegate.handleException(exception, context);
            } catch (final RuntimeException thrown) {
              exceptionFuture.complete(thrown);
              throw thrown;
            }
          }
        };

    bus = new AsyncEventBus(executor, exceptionHandlerRecordingWrapper);
  }

  @Test
  void logWarningIfAssertFails() throws ExecutionException, InterruptedException {
    bus.register(
        new Object() {
          @Subscribe
          void onString(final String test) {
            checkArgument("other".equals(test), "Assertion failed");
          }
        });
    bus.post("test");

    assertEquals(Level.WARN, logLevelFuture.get());
    assertFalse(exceptionFuture.isDone());
  }

  @Test
  void logFatalAndRethrowIfNonAssertExceptionThrown()
      throws ExecutionException, InterruptedException {
    bus.register(
        new Object() {
          @Subscribe
          void onString(String test) {
            throw new RuntimeException("test");
          }
        });
    bus.post("test");

    assertEquals(Level.FATAL, logLevelFuture.get());

    var exception = exceptionFuture.get();
    assertTrue(exception instanceof RuntimeException);
    assertTrue(exception.getCause() instanceof RuntimeException);
  }
}
