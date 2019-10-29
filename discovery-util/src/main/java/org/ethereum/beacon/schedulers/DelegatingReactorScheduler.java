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

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import reactor.core.Disposable;
import reactor.core.scheduler.Scheduler;

public class DelegatingReactorScheduler implements reactor.core.scheduler.Scheduler {

  protected final reactor.core.scheduler.Scheduler delegate;
  protected final Supplier<Long> timeSupplier;

  public DelegatingReactorScheduler(Scheduler delegate, Supplier<Long> timeSupplier) {
    this.delegate = delegate;
    this.timeSupplier = timeSupplier;
  }

  @Nonnull
  @Override
  public Disposable schedule(@Nonnull Runnable task) {
    return delegate.schedule(task);
  }

  @Nonnull
  @Override
  public Disposable schedule(Runnable task, long delay, TimeUnit unit) {
    return delegate.schedule(task, delay, unit);
  }

  @Nonnull
  @Override
  public Disposable schedulePeriodically(
      Runnable task, long initialDelay, long period, TimeUnit unit) {
    return delegate.schedulePeriodically(task, initialDelay, period, unit);
  }

  @Override
  public long now(TimeUnit unit) {
    return unit.convert(timeSupplier.get(), TimeUnit.MILLISECONDS);
  }

  @Nonnull
  @Override
  public Worker createWorker() {
    return delegate.createWorker();
  }

  @Override
  public void dispose() {
    delegate.dispose();
  }

  @Override
  public void start() {
    delegate.start();
  }

  @Override
  public boolean isDisposed() {
    return delegate.isDisposed();
  }

  public static class DelegateWorker implements Worker {
    protected final Worker delegate;

    public DelegateWorker(Worker delegate) {
      this.delegate = delegate;
    }

    @Nonnull
    @Override
    public Disposable schedule(@Nonnull Runnable task) {
      return delegate.schedule(task);
    }

    @Nonnull
    @Override
    public Disposable schedule(Runnable task, long delay, TimeUnit unit) {
      return delegate.schedule(task, delay, unit);
    }

    @Nonnull
    @Override
    public Disposable schedulePeriodically(
        Runnable task, long initialDelay, long period, TimeUnit unit) {
      return delegate.schedulePeriodically(task, initialDelay, period, unit);
    }

    @Override
    public void dispose() {
      delegate.dispose();
    }

    @Override
    public boolean isDisposed() {
      return delegate.isDisposed();
    }
  }
}
