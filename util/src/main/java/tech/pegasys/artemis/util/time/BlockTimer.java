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

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.util.Date;

/**
 * This timer ticks on one of two events: external event raised, or elapsed internal timer. If the
 * external event cause the tick, the internal timer is reset.
 */

public class BlockTimer implements Timer {

  private final Long EPSILON = 1000L;
  private final Long timeInterval;
  private Long previousTime = new Date(Long.MIN_VALUE).getTime();

  private EventBus eventBus;

  public BlockTimer(EventBus eventBus, Long startDelay, Long interval) {
    this.eventBus = eventBus;
    this.timeInterval = interval;
    try {
      Thread.sleep(startDelay);
    } catch (InterruptedException e) {
      System.out.println("Timer failed");
    }
  }

  @Override
  public void start() {
    this.eventBus.register(this);
    eventBus.post(Long.valueOf(new Date().getTime()));
  }

  @Override
  public void stop() {
    this.eventBus.unregister(this);
  }

  private synchronized void scheduleTimer(Long currentTime, Long elapsedTime) {
    // This condition evaluates true when relativeTick is within this range:
    // [timerInterval - EPSILON, timerInterval + EPSILON]
    if (elapsedTime <= timeInterval + EPSILON && elapsedTime > timeInterval - EPSILON) {
      // notify subscribers on the event bus
      eventBus.post(new Date(currentTime));
      previousTime = currentTime;
    }
  }

  @Subscribe
  public void onBlockReceived(Long time) {
    System.out.println("###########################time: " + time);
    this.eventBus.post(new Date(time));
  }
}
