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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.Assert;
import org.junit.Test;

public class ControlledSchedulersTest {

  @Test
  public void test1() throws ExecutionException, InterruptedException {
    ControlledSchedulers sch1 = Schedulers.createControlled();
    ControlledSchedulers sch2 = Schedulers.createControlled();
    TimeController timeController = new TimeControllerImpl();
    timeController.setTime(1000);

    sch1.getTimeController().setParent(timeController);
    sch2.getTimeController().setParent(timeController);

    CompletableFuture[] f = new CompletableFuture[64];
    f[0] =
        sch1.blocking()
            .executeWithDelay(
                Duration.ofMillis(10),
                () -> {
                  System.out.println("sch1.1, task 1");
                  f[1] =
                      sch1.cpuHeavy()
                          .executeWithDelay(
                              Duration.ofMillis(0), () -> System.out.println("sch1.2, task 1"));
                  f[2] =
                      sch1.cpuHeavy()
                          .executeWithDelay(
                              Duration.ofMillis(2), () -> System.out.println("sch1.2, task 2"));
                  f[3] =
                      sch2.blocking()
                          .executeWithDelay(
                              Duration.ofMillis(0), () -> System.out.println("sch2.1, task 1"));
                  f[4] =
                      sch2.blocking()
                          .executeWithDelay(
                              Duration.ofMillis(2), () -> System.out.println("sch2.1, task 2"));
                  f[5] =
                      sch2.blocking()
                          .executeWithDelay(
                              Duration.ofMillis(2), () -> System.out.println("sch2.1, task 3"));
                });
    f[6] =
        sch2.blocking()
            .executeWithDelay(
                Duration.ofMillis(10),
                () -> {
                  System.out.println("sch2.1, task 10");
                  f[7] =
                      sch1.cpuHeavy()
                          .executeWithDelay(
                              Duration.ofMillis(0), () -> System.out.println("sch1.2, task 10"));
                  f[8] =
                      sch1.cpuHeavy()
                          .executeWithDelay(
                              Duration.ofMillis(2), () -> System.out.println("sch1.2, task 12"));
                  f[9] =
                      sch2.blocking()
                          .executeWithDelay(
                              Duration.ofMillis(0), () -> System.out.println("sch2.1, task 11"));
                  f[10] =
                      sch2.blocking()
                          .executeWithDelay(
                              Duration.ofMillis(2), () -> System.out.println("sch2.1, task 12"));
                  f[11] =
                      sch2.blocking()
                          .executeWithDelay(
                              Duration.ofMillis(2), () -> System.out.println("sch2.1, task 13"));
                });
    timeController.setTime(1009);
    for (CompletableFuture ff : f) {
      if (ff != null) Assert.assertFalse(ff.isDone());
    }

    timeController.setTime(1010);
    Assert.assertTrue(f[0].isDone());
    Assert.assertTrue(f[1].isDone());
    Assert.assertFalse(f[2].isDone());
    Assert.assertTrue(f[3].isDone());
    Assert.assertFalse(f[4].isDone());
    Assert.assertFalse(f[5].isDone());
    Assert.assertTrue(f[6].isDone());
    Assert.assertTrue(f[7].isDone());
    Assert.assertFalse(f[8].isDone());
    Assert.assertTrue(f[9].isDone());
    Assert.assertFalse(f[10].isDone());
    Assert.assertFalse(f[11].isDone());

    timeController.setTime(1011);

    Assert.assertTrue(f[0].isDone());
    Assert.assertTrue(f[1].isDone());
    Assert.assertFalse(f[2].isDone());
    Assert.assertTrue(f[3].isDone());
    Assert.assertFalse(f[4].isDone());
    Assert.assertFalse(f[5].isDone());
    Assert.assertTrue(f[6].isDone());
    Assert.assertTrue(f[7].isDone());
    Assert.assertFalse(f[8].isDone());
    Assert.assertTrue(f[9].isDone());
    Assert.assertFalse(f[10].isDone());
    Assert.assertFalse(f[11].isDone());

    f[5].cancel(true);
    f[11].cancel(true);

    timeController.setTime(1012);

    for (CompletableFuture ff : f) {
      if (ff != null) Assert.assertTrue(ff.isDone());
    }

    f[4].get();
    try {
      f[5].get();
      Assert.fail();
    } catch (CancellationException e) {
    }
    f[10].get();
    try {
      f[11].get();
      Assert.fail();
    } catch (CancellationException e) {
    }
  }
}
