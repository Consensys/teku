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

package tech.pegasys.artemis.util.time;

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * This timer ticks on one of two events: external event raised, or elapsed internal timer. If the
 * external event cause the tick, the internal timer is reset.
 */
public class BlockTimer implements Timer {

  private final Long EPSILON = 100L;
  private final Long STEPS = 32L;
  private final Long timeInterval;
  private Long previousTime = new Date(Long.MIN_VALUE).getTime();
  private Long totalDrift = 0L;

  private EventBus eventBus;
  private EventBus internalEventBus;
  private QuartzTimer internalTimer;

  public BlockTimer(EventBus eventBus, Long startDelay, Long interval) {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    this.internalEventBus = new AsyncEventBus(executor);
    this.eventBus = eventBus;
    this.internalEventBus.register(this);
    this.timeInterval = interval;
    try {
      this.internalTimer = new QuartzTimer(internalEventBus, startDelay, interval);
    } catch (IllegalArgumentException e) {
      System.exit(1);
    }
  }

  @Override
  public void start() {
    internalTimer.start();
    this.eventBus.register(this);
  }

  @Override
  public void stop() {
    internalTimer.stop();
    this.eventBus.unregister(this);
  }

  private synchronized void scheduleTimer(Long currentTime, Long elapsedTime) {
    // This condition evaluates true when relativeTick is within this range:
    // [timerInterval - EPSILON, timerInterval + EPSILON]
    if (elapsedTime <= timeInterval + EPSILON && elapsedTime > timeInterval - EPSILON) {
      internalTimer.stop();
      System.out.println("timeInterval + EPSILON: " + (timeInterval + EPSILON));
      System.out.println("elapsed time: " + elapsedTime);
      System.out.println("timeInterval - EPSILON: " + (timeInterval - EPSILON));
      previousTime = currentTime;
      Long drift = timeInterval - elapsedTime;
      totalDrift += drift;
      internalTimer =
          new QuartzTimer(internalEventBus, timeInterval + totalDrift / STEPS, timeInterval);
      internalTimer.start();
      // notify subscribers on the event bus
      eventBus.post(new Date(previousTime));
      System.out.println("##################################TotalDrift = " + totalDrift);
    }
  }

  @Subscribe
  public void onLocalTick(Date date) {
    System.out.println("onLocalTick");
    Long localTime = date.getTime();
    Long elapsedLocalTime = localTime - previousTime;
    if (previousTime.equals(new Date(Long.MIN_VALUE).getTime())) {
      elapsedLocalTime = timeInterval;
    }
    scheduleTimer(localTime, elapsedLocalTime);
  }

  @Subscribe
  public void onRelativeTick(Long time) {
    System.out.println("onRelativeTick");
    Long relativeTime = new Date().getTime();
    Long elapsedRelativeTime = relativeTime - previousTime;
    if (previousTime.equals(new Date(Long.MIN_VALUE).getTime())) {
      elapsedRelativeTime = timeInterval;
    }
    scheduleTimer(relativeTime, elapsedRelativeTime);
  }
}
