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

package org.ethereum.beacon.schedulers;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DefaultSchedulers extends AbstractSchedulers {

  private static final Logger logger = LogManager.getLogger(DefaultSchedulers.class);

  private Consumer<Throwable> errorHandler = t -> logger.error("Unhandled exception:", t);
  private volatile boolean started;

  public void setErrorHandler(Consumer<Throwable> errorHandler) {
    if (started) {
      throw new IllegalStateException("ErrorHandler should be set up prior to any other calls");
    }
    this.errorHandler = errorHandler;
  }

  @Override
  protected Scheduler createExecutorScheduler(ScheduledExecutorService executorService) {
    return new ErrorHandlingScheduler(
        new ExecutorScheduler(executorService, this::getCurrentTime), errorHandler);
  }

  @Override
  protected ScheduledExecutorService createExecutor(String namePattern, int threads) {
    started = true;
    return Executors.newScheduledThreadPool(threads, createThreadFactory(namePattern));
  }

  protected ThreadFactory createThreadFactory(String namePattern) {
    return createThreadFactoryBuilder(namePattern).build();
  }

  protected ThreadFactoryBuilder createThreadFactoryBuilder(String namePattern) {
    return new ThreadFactoryBuilder()
        .setDaemon(true)
        .setNameFormat(namePattern)
        .setUncaughtExceptionHandler((thread, thr) -> errorHandler.accept(thr));
  }
}
