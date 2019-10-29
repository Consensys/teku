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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.RunnableScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Assert;
import org.junit.Test;

public class LatestExecutorTest {

  @Test
  public void test1() throws InterruptedException {
    AtomicInteger tasksCounter = new AtomicInteger();
    ScheduledThreadPoolExecutor executor =
        new ScheduledThreadPoolExecutor(1) {
          @Override
          protected <V> RunnableScheduledFuture<V> decorateTask(
              Runnable runnable, RunnableScheduledFuture<V> task) {
            tasksCounter.incrementAndGet();
            return super.decorateTask(runnable, task);
          }

          @Override
          protected <V> RunnableScheduledFuture<V> decorateTask(
              Callable<V> callable, RunnableScheduledFuture<V> task) {
            tasksCounter.incrementAndGet();
            return super.decorateTask(callable, task);
          }
        };
    ExecutorScheduler scheduler = new ExecutorScheduler(executor, System::currentTimeMillis);

    List<Integer> processedEvents = new ArrayList<>();
    LatestExecutor<Integer> latestExecutor =
        new LatestExecutor<>(
            scheduler,
            i -> {
              processedEvents.add(i);
              try {
                Thread.sleep(50);
              } catch (InterruptedException e) {
                e.printStackTrace();
              }
            });

    for (int i = 0; i < 100; i++) {
      latestExecutor.newEvent(i);
    }
    Thread.sleep(300);

    Assert.assertEquals(99, (int) processedEvents.get(processedEvents.size() - 1));
    Assert.assertTrue(processedEvents.size() < 3);
    Assert.assertTrue(tasksCounter.get() < 3);

    processedEvents.clear();
    tasksCounter.set(0);

    latestExecutor.newEvent(200);
    Thread.sleep(200);

    Assert.assertEquals(200, (int) processedEvents.get(0));
    Assert.assertTrue(processedEvents.size() == 1);
    Assert.assertTrue(tasksCounter.get() == 1);

    processedEvents.clear();
    tasksCounter.set(0);

    latestExecutor.newEvent(300);
    Thread.sleep(10);
    latestExecutor.newEvent(301);

    Thread.sleep(300);

    Assert.assertEquals(301, (int) processedEvents.get(processedEvents.size() - 1));
    Assert.assertTrue(processedEvents.size() < 3);
    Assert.assertTrue(tasksCounter.get() < 3);
  }
}
