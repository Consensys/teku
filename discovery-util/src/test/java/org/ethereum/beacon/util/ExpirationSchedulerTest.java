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

package org.ethereum.beacon.util;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class ExpirationSchedulerTest {
  @Test
  public void test() throws Exception {
    ExpirationScheduler<Integer> scheduler1 = new ExpirationScheduler(500, TimeUnit.MILLISECONDS);
    ExpirationScheduler<Integer> scheduler2 = new ExpirationScheduler(500, TimeUnit.MILLISECONDS);
    CountDownLatch first = new CountDownLatch(1);
    CountDownLatch second = new CountDownLatch(1);
    CountDownLatch third = new CountDownLatch(1);
    CountDownLatch fourth = new CountDownLatch(1);
    CountDownLatch fifth = new CountDownLatch(1);
    scheduler1.put(5, fifth::countDown);
    Thread.sleep(100);
    scheduler1.put(
        1,
        () -> {
          first.countDown();
          assert second.getCount() == 1;
          assert third.getCount() == 1;
          assert fourth.getCount() == 1;
          assert fifth.getCount() == 1;
        });
    scheduler2.put(
        2,
        () -> {
          second.countDown();
          assert first.getCount() == 0;
          assert third.getCount() == 1;
          assert fourth.getCount() == 1;
          assert fifth.getCount() == 1;
        });
    scheduler2.put(
        3,
        () -> {
          third.countDown();
          assert first.getCount() == 0;
          assert second.getCount() == 0;
          assert fourth.getCount() == 1;
          assert fifth.getCount() == 1;
        });
    scheduler2.put(
        4,
        () -> {
          fourth.countDown();
          assert first.getCount() == 0;
          assert second.getCount() == 0;
          assert third.getCount() == 0;
          assert fifth.getCount() == 1;
        });

    Thread.sleep(50);
    scheduler1.put(5, fifth::countDown);

    assert first.await(1, TimeUnit.SECONDS);
    assert second.await(100, TimeUnit.MILLISECONDS);
    assert third.await(100, TimeUnit.MILLISECONDS);
    assert fourth.await(100, TimeUnit.MILLISECONDS);
    assert fifth.await(100, TimeUnit.MILLISECONDS);
  }
}
