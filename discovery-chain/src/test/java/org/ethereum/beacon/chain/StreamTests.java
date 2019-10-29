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

package org.ethereum.beacon.chain;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.ReplayProcessor;
import reactor.core.scheduler.Schedulers;

public class StreamTests {

  FluxSink<Long> sink;

  @Test
  public void test1() throws InterruptedException {
    ReplayProcessor<Long> processor = ReplayProcessor.cacheLast();

    Publisher<Long> stream = Flux.from(processor).publishOn(Schedulers.single());

    for (int i = 0; i < 10; i++) {
      processor.onNext((long) i);
    }

    Flux.from(stream)
        .doOnSubscribe(s -> System.out.println("#1: subscribe"))
        .doOnNext(l -> System.out.println("#1: " + l))
        .subscribe();
    Flux.from(stream)
        .doOnSubscribe(s -> System.out.println("#2: subscribe"))
        .doOnNext(l -> System.out.println("#2: " + l))
        .subscribe();

    Thread.sleep(200);

    for (int i = 10; i < 20; i++) {
      processor.onNext((long) i);
      Thread.sleep(20);
    }

    Thread.sleep(1000L);
  }

  //  @Test
  //  @Ignore
  public void intervalTest1() throws InterruptedException {

    long initDelay = (System.currentTimeMillis() / 10000 + 1) * 10000 - System.currentTimeMillis();
    Flux<Long> interval = Flux.interval(Duration.ofMillis(initDelay), Duration.ofSeconds(10));
    Thread.sleep(2000);

    Disposable subscribe =
        interval.subscribe(l -> System.out.println(l + ": " + System.currentTimeMillis() % 60_000));
    Thread.sleep(20000);

    subscribe.dispose();
    System.out.println("Unsubscribed");
    Thread.sleep(2000);

    interval.subscribe(l -> System.out.println(l + ": " + System.currentTimeMillis() % 60_000));
    Thread.sleep(20000);
  }
}
