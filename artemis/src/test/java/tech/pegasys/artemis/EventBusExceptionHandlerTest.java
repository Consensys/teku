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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.eventbus.SubscriberExceptionContext;
import com.google.common.eventbus.SubscriberExceptionHandler;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.util.Waiter;
import tech.pegasys.artemis.util.async.SafeFuture;
import tech.pegasys.teku.logging.ALogger;

class EventBusExceptionHandlerTest {

  private static ExecutorService executor;

  private EventBus bus;

  private final SafeFuture<Level> logLevelFuture = new SafeFuture<>();
  private final SafeFuture<Throwable> handledException = new SafeFuture<>();
  private final SafeFuture<Throwable> unhandledExceptionFuture = new SafeFuture<>();

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
        new ALogger("stdout") {
          @Override
          public void log(final Level level, final String message) {
            logLevelFuture.complete(level);
          }

          @Override
          public void log(Level level, String message, Throwable throwable, Color color) {
            handledException.complete(throwable);
            logLevelFuture.complete(level);
          }

          @Override
          public void log(Level level, String message, Throwable throwable) {
            handledException.complete(throwable);
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
              unhandledExceptionFuture.complete(thrown);
              throw thrown;
            }
          }
        };

    bus = new AsyncEventBus(executor, exceptionHandlerRecordingWrapper);
  }

  @Test
  void logWarningIfAssertFails() throws Exception {
    final IllegalArgumentException exception = new IllegalArgumentException("whoops");
    bus.register(
        new Object() {
          @Subscribe
          void onString(final String test) {
            throw exception;
          }
        });
    bus.post("test");

    Waiter.waitFor(logLevelFuture);
    assertThat(logLevelFuture).isCompletedWithValue(Level.WARN);
    assertThat(unhandledExceptionFuture).isNotDone();
    assertThat(handledException).isCompletedWithValue(exception);
  }

  @Test
  void logFatalIfNonAssertExceptionThrown() throws Exception {
    final RuntimeException exception = new RuntimeException("test");
    bus.register(
        new Object() {
          @Subscribe
          void onString(String test) {
            throw exception;
          }
        });
    bus.post("test");

    Waiter.waitFor(logLevelFuture);
    assertThat(logLevelFuture).isCompletedWithValue(Level.FATAL);
    assertThat(unhandledExceptionFuture).isNotDone();
    assertThat(handledException).isCompletedWithValue(exception);
  }
}
