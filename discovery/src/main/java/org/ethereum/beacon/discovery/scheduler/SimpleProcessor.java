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

package org.ethereum.beacon.discovery.scheduler;

// import org.ethereum.beacon.schedulers.Scheduler;
import org.reactivestreams.Processor;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxProcessor;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.ReplayProcessor;

public class SimpleProcessor<T> implements Processor<T, T> {
  FluxProcessor<T, T> subscriber;
  FluxSink<T> sink;
  Flux<T> publisher;
  boolean subscribed;

  public SimpleProcessor(Scheduler scheduler, String name, T initialValue) {
    this(scheduler.toReactor(), name);
    onNext(initialValue);
  }

  public SimpleProcessor(Scheduler scheduler, String name) {
    this(scheduler.toReactor(), name);
  }

  public SimpleProcessor(reactor.core.scheduler.Scheduler scheduler, String name) {
    ReplayProcessor<T> processor = ReplayProcessor.cacheLast();
    subscriber = processor;
    sink = subscriber.sink();
    publisher = Flux.from(processor).publishOn(scheduler).onBackpressureError().name(name);
  }

  public SimpleProcessor doOnAnySubscribed(Runnable handler) {
    publisher =
        publisher.doOnSubscribe(
            s -> {
              if (!subscribed) {
                subscribed = true;
                handler.run();
              }
            });
    return this;
  }

  public SimpleProcessor doOnNoneSubscribed(Runnable handler) {
    publisher =
        publisher.doOnCancel(
            () -> {
              if (subscribed && !subscriber.hasDownstreams()) {
                subscribed = false;
                handler.run();
              }
            });
    return this;
  }

  @Override
  public void subscribe(Subscriber<? super T> subscriber) {
    publisher.subscribe(subscriber);
  }

  @Override
  public void onSubscribe(Subscription subscription) {
    subscriber.onSubscribe(subscription);
  }

  @Override
  public void onNext(T t) {
    sink.next(t);
  }

  @Override
  public void onError(Throwable throwable) {
    sink.error(throwable);
  }

  @Override
  public void onComplete() {
    sink.complete();
  }
}
