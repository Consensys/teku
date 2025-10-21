/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.infrastructure.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class FutureValueObserverTest {

  @Test
  public void notDeliversWhenNotComplete() throws Exception {
    final CountDownLatch latch = new CountDownLatch(1);
    final FutureValueObserver<Integer> observer = new FutureValueObserver<>();
    observer.subscribe(
        newValue -> {
          latch.countDown();
        });
    assertThat(latch.await(500, TimeUnit.MILLISECONDS)).isFalse();
  }

  @Test
  public void subscribeBeforeCompletion() throws Exception {
    final CountDownLatch latch = new CountDownLatch(1);
    final FutureValueObserver<Integer> observer = new FutureValueObserver<>();
    observer.subscribe(
        newValue -> {
          assertThat(newValue).isEqualTo(1);
          latch.countDown();
        });
    observer.complete(c -> c.onValueChanged(1));
    latch.await();
  }

  @Test
  public void subscribeAfterCompletion() throws Exception {
    final CountDownLatch latch = new CountDownLatch(1);
    final FutureValueObserver<Integer> observer = new FutureValueObserver<>();
    observer.complete(c -> c.onValueChanged(2));
    observer.subscribe(
        newValue -> {
          assertThat(newValue).isEqualTo(2);
          latch.countDown();
        });
    latch.await();
  }
}
