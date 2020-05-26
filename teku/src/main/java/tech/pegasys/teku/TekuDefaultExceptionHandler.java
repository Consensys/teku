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

package tech.pegasys.teku;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.SubscriberExceptionContext;
import com.google.common.eventbus.SubscriberExceptionHandler;
import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.reflect.Method;
import java.util.function.Function;
import tech.pegasys.teku.events.ChannelExceptionHandler;
import tech.pegasys.teku.logging.StatusLogger;

public final class TekuDefaultExceptionHandler
    implements SubscriberExceptionHandler,
        ChannelExceptionHandler,
        UncaughtExceptionHandler,
        Function<Throwable, Void> {

  private final StatusLogger log;

  public TekuDefaultExceptionHandler() {
    this(StatusLogger.STATUS_LOG);
  }

  @VisibleForTesting
  TekuDefaultExceptionHandler(final StatusLogger log) {
    this.log = log;
  }

  @Override
  public void handleException(final Throwable exception, final SubscriberExceptionContext context) {
    handleException(
        exception,
        "event '"
            + context.getEvent().getClass().getName()
            + "'"
            + " in handler '"
            + context.getSubscriber().getClass().getName()
            + "'"
            + " (method  '"
            + context.getSubscriberMethod().getName()
            + "')");
  }

  @Override
  public void handleException(
      final Throwable error,
      final Object subscriber,
      final Method invokedMethod,
      final Object[] args) {
    handleException(
        error,
        "event '"
            + invokedMethod.getDeclaringClass()
            + "."
            + invokedMethod.getName()
            + "' in handler '"
            + subscriber.getClass().getName()
            + "'");
  }

  @Override
  public void uncaughtException(final Thread t, final Throwable e) {
    handleException(e, t.getName());
  }

  private void handleException(final Throwable exception, final String subscriberDescription) {
    if (isSpecFailure(exception)) {
      log.specificationFailure(subscriberDescription, exception);
    } else {
      log.unexpectedFailure(subscriberDescription, exception);
    }
    if (exception instanceof OutOfMemoryError) {
      System.exit(2);
    }
  }

  private static boolean isSpecFailure(final Throwable exception) {
    return exception instanceof IllegalArgumentException;
  }

  @Override
  public Void apply(final Throwable throwable) {
    return null;
  }
}
