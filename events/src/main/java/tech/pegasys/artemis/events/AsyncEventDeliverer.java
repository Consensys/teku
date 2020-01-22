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

package tech.pegasys.artemis.events;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Collections.synchronizedMap;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AsyncEventDeliverer<T> extends EventDeliverer<T> {
  private static final Logger LOG = LogManager.getLogger();
  private static final int QUEUE_CAPACITY = 500;

  private final Map<T, Queue<Runnable>> eventQueuesBySubscriber =
      synchronizedMap(new IdentityHashMap<>());
  private final ExecutorService executor;

  public AsyncEventDeliverer(final ExecutorService executor) {
    this.executor = executor;
  }

  @Override
  void subscribe(final T subscriber) {
    final BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    eventQueuesBySubscriber.put(subscriber, queue);
    executor.execute(new QueueReader(queue));
    super.subscribe(subscriber);
  }

  @Override
  protected void deliverTo(final T subscriber, final Method method, final Object[] args) {
    final Queue<Runnable> queue = checkNotNull(eventQueuesBySubscriber.get(subscriber));
    queue.add(
        () -> {
          try {
            method.invoke(subscriber, args);
          } catch (IllegalAccessException | InvocationTargetException e) {
            LOG.error(
                "Failed to deliver " + method.getName() + " event to " + subscriber.getClass(), e);
          }
        });
  }

  @Override
  public void stop() {
    executor.shutdownNow();
  }

  static class QueueReader implements Runnable {

    private final BlockingQueue<Runnable> queue;

    public QueueReader(final BlockingQueue<Runnable> queue) {
      this.queue = queue;
    }

    @Override
    public void run() {
      try {
        while (!Thread.interrupted()) {
          deliverNextEvent();
        }
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    void deliverNextEvent() throws InterruptedException {
      queue.take().run();
    }
  }
}
