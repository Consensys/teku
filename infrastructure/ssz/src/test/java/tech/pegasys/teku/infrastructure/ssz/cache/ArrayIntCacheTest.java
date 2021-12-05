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

package tech.pegasys.teku.infrastructure.ssz.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class ArrayIntCacheTest {

  private final List<Thread> startedThreads = new ArrayList<>();

  @AfterEach
  void clear() {
    startedThreads.forEach(Thread::interrupt);
    startedThreads.clear();
  }

  @Test
  // The threading test is probabilistic and may have false positives
  // (i.e. pass on incorrect implementation)
  public void testThreadSafety() throws InterruptedException {
    ArrayIntCache<String> cache = new ArrayIntCache<>(4);
    Thread t1 =
        new Thread(
            () -> {
              while (!Thread.interrupted()) {
                String val = cache.get(1024, idx -> "aaa");
                assertThat(val).isEqualTo("aaa");
                cache.invalidateWithNewValueInt(1024, "aaa");
              }
            });
    startedThreads.add(t1);
    t1.start();

    List<Thread> threads =
        IntStream.range(0, 16)
            .mapToObj(
                i ->
                    new Thread(
                        () -> {
                          while (!Thread.interrupted()) {
                            IntCache<String> cache1 = cache.transfer();
                            String val = cache1.get(1024, idx -> "aaa");
                            assertThat(val).isEqualTo("aaa");
                            for (int j = 0; j < 100; j++) {
                              cache1.invalidateWithNewValueInt(1024, "bbb");
                              String val1 = cache1.get(1024, idx -> "bbb");
                              assertThat(val1).isEqualTo("bbb");
                            }
                          }
                        }))
            .peek(Thread::start)
            .collect(Collectors.toList());
    startedThreads.addAll(threads);

    // wait a second for any threading issues
    t1.join(1000);
    assertThat(t1.isAlive()).isTrue();
    assertThat(threads).allMatch(Thread::isAlive);
  }
}
