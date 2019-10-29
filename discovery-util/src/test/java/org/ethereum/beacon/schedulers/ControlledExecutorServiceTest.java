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

import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Assert;
import org.junit.Test;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

public class ControlledExecutorServiceTest {

  @Test
  public void testSchedule1() throws Exception {
    ControlledExecutorServiceImpl executor = new ControlledExecutorServiceImpl();
    TimeController timeController = new TimeControllerImpl();
    executor.setTimeController(timeController);

    ScheduledFuture<Integer> f1 = executor.schedule(() -> 111, 6, TimeUnit.MILLISECONDS);
    ScheduledFuture<Integer> f2 =
        executor.schedule(
            () -> {
              Assert.assertEquals(10, executor.getCurrentTime());
              return 222;
            },
            10,
            TimeUnit.MILLISECONDS);
    ScheduledFuture<Integer> f3 =
        executor.schedule(
            () -> {
              Assert.assertEquals(10, executor.getCurrentTime());
              return 333;
            },
            10,
            TimeUnit.MILLISECONDS);
    timeController.setTime(5);
    Assert.assertFalse(f1.isDone());
    Assert.assertFalse(f2.isDone());
    Assert.assertFalse(f3.isDone());
    timeController.setTime(6);
    Assert.assertTrue(f1.isDone());
    Assert.assertEquals(111, (long) f1.get());
    Assert.assertFalse(f2.isDone());
    Assert.assertFalse(f3.isDone());
    timeController.setTime(15);
    Assert.assertTrue(f2.isDone());
    Assert.assertTrue(f3.isDone());
    Assert.assertEquals(222, (long) f2.get());
    Assert.assertEquals(333, (long) f3.get());

    ScheduledFuture<Integer>[] f5 = new ScheduledFuture[1];
    ScheduledFuture<Void> f4 =
        executor.schedule(
            () -> {
              f5[0] = executor.schedule(() -> 444, 5, TimeUnit.MILLISECONDS);
              throw new Exception("Aaaaa");
            },
            10,
            TimeUnit.MILLISECONDS);

    timeController.setTime(16);
    Assert.assertFalse(f4.isDone());
    Assert.assertNull(f5[0]);

    timeController.setTime(100);
    Assert.assertTrue(f4.isDone());
    Assert.assertTrue(f5[0].isDone());
    try {
      f4.get();
      Assert.fail();
    } catch (ExecutionException e) {
      System.out.println("Expected f4 exception: " + e);
    }
    Assert.assertEquals(444, (int) f5[0].get());

    boolean[] r1 = new boolean[1];
    ScheduledFuture<Integer> f6 =
        executor.schedule(
            () -> {
              r1[0] = true;
              return 666;
            },
            5,
            TimeUnit.MILLISECONDS);
    timeController.setTime(102);
    Assert.assertFalse(f6.isDone());
    boolean cancel = f6.cancel(true);
    Assert.assertTrue(cancel);
    Assert.assertTrue(f6.isDone());
    Assert.assertTrue(f6.isCancelled());
    try {
      f6.get();
      Assert.fail();
    } catch (CancellationException e) {
      System.out.println("expected: " + e);
    }
    Assert.assertFalse(r1[0]);

    timeController.setTime(200);
    Assert.assertFalse(r1[0]);
  }

  @Test
  public void testPeriodicSchedule1() throws Exception {
    ControlledExecutorServiceImpl executor = new ControlledExecutorServiceImpl();
    TimeController timeController = new TimeControllerImpl();
    executor.setTimeController(timeController);

    AtomicInteger cnt1 = new AtomicInteger();
    ScheduledFuture<?>[] f1 = new ScheduledFuture[1];
    f1[0] =
        executor.scheduleAtFixedRate(
            () -> {
              if (cnt1.incrementAndGet() == 3) {
                f1[0].cancel(true);
              }
            },
            5,
            10,
            TimeUnit.MILLISECONDS);
    AtomicInteger cnt2 = new AtomicInteger();
    ScheduledFuture<?>[] f2 = new ScheduledFuture[1];
    f2[0] =
        executor.scheduleAtFixedRate(
            () -> {
              cnt2.incrementAndGet();
            },
            5,
            10,
            TimeUnit.MILLISECONDS);
    AtomicInteger cnt3 = new AtomicInteger();
    ScheduledFuture<?>[] f3 = new ScheduledFuture[1];
    f3[0] =
        executor.scheduleAtFixedRate(
            () -> {
              if (cnt3.incrementAndGet() == 3) {
                f3[0].cancel(true);
              }
            },
            7,
            10,
            TimeUnit.MILLISECONDS);

    timeController.setTime(2);
    Assert.assertEquals(0, cnt1.get());
    Assert.assertEquals(0, cnt2.get());
    Assert.assertEquals(0, cnt3.get());
    Assert.assertFalse(f1[0].isCancelled());
    Assert.assertFalse(f2[0].isCancelled());
    Assert.assertFalse(f3[0].isCancelled());

    timeController.setTime(6);
    Assert.assertEquals(1, cnt1.get());
    Assert.assertEquals(1, cnt2.get());
    Assert.assertEquals(0, cnt3.get());
    Assert.assertFalse(f1[0].isCancelled());
    Assert.assertFalse(f2[0].isCancelled());
    Assert.assertFalse(f3[0].isCancelled());

    timeController.setTime(8);
    Assert.assertEquals(1, cnt1.get());
    Assert.assertEquals(1, cnt2.get());
    Assert.assertEquals(1, cnt3.get());
    Assert.assertFalse(f1[0].isCancelled());
    Assert.assertFalse(f2[0].isCancelled());
    Assert.assertFalse(f3[0].isCancelled());

    timeController.setTime(88);
    Assert.assertEquals(3, cnt1.get());
    Assert.assertEquals(9, cnt2.get());
    Assert.assertEquals(3, cnt3.get());
    Assert.assertTrue(f1[0].isCancelled());
    Assert.assertFalse(f2[0].isCancelled());
    Assert.assertTrue(f3[0].isCancelled());

    f2[0].cancel(true);

    timeController.setTime(200);
    Assert.assertEquals(3, cnt1.get());
    Assert.assertEquals(9, cnt2.get());
    Assert.assertEquals(3, cnt3.get());
    Assert.assertTrue(f1[0].isCancelled());
    Assert.assertTrue(f2[0].isCancelled());
    Assert.assertTrue(f3[0].isCancelled());
  }

  @Test
  public void testFluxInterval() throws Exception {
    ControlledExecutorServiceImpl executor = new ControlledExecutorServiceImpl();
    TimeController timeController = new TimeControllerImpl();
    executor.setTimeController(timeController);
    Scheduler reactScheduler = Schedulers.fromExecutor(executor);

    AtomicLong r1 = new AtomicLong(-1);
    Disposable subscribe1 =
        Flux.interval(Duration.ofMillis(3), Duration.ofMillis(10), reactScheduler)
            .subscribe(l -> r1.set(l));

    AtomicLong r2 = new AtomicLong(-1);
    Disposable subscribe2 =
        Flux.interval(Duration.ofMillis(6), Duration.ofMillis(10), reactScheduler)
            .subscribe(l -> r2.set(l));

    timeController.setTime(2);
    Assert.assertEquals(-1, r1.get());
    Assert.assertEquals(-1, r2.get());

    timeController.setTime(3);
    Assert.assertEquals(0, r1.get());
    Assert.assertEquals(-1, r2.get());

    timeController.setTime(4);
    Assert.assertEquals(0, r1.get());
    Assert.assertEquals(-1, r2.get());

    timeController.setTime(10);
    Assert.assertEquals(0, r1.get());
    Assert.assertEquals(0, r2.get());

    timeController.setTime(100);
    Assert.assertEquals(9, r1.get());
    Assert.assertEquals(9, r2.get());

    subscribe1.dispose();
    timeController.setTime(200);
    Assert.assertEquals(9, r1.get());
    Assert.assertEquals(19, r2.get());
  }
}
