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
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class JavaTimer implements Timer {

  private ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
  private ScheduledEvent task;
  private int startDelay;
  private int interval;

  @SuppressWarnings({"unchecked", "rawtypes"})
  public JavaTimer(EventBus eventBus, Integer startDelay, Integer interval)
      throws IllegalArgumentException {
    try {
      Class type = ScheduledEvent.class;
      Constructor constructor = type.getConstructor(new Class[] {EventBus.class});
      this.task = (ScheduledEvent) constructor.newInstance(new Object[] {eventBus});
    } catch (InstantiationException e) {
      throw new IllegalArgumentException(
          "In JavaTimer a InstantiationException was thrown: " + e.toString());
    } catch (IllegalAccessException e) {
      throw new IllegalArgumentException(
          "In JavaTimer a IllegalAccessException was thrown: " + e.toString());
    } catch (InvocationTargetException e) {
      throw new IllegalArgumentException(
          "In JavaTimer a InvocationTargetException was thrown: " + e.toString());
    } catch (NoSuchMethodException e) {
      throw new IllegalArgumentException(
          "In JavaTimer a NoSuchMethodException was thrown: " + e.toString());
    }
    this.startDelay = startDelay;
    this.interval = interval;
  }

  @Override
  public void start() {
    scheduler.scheduleAtFixedRate(task, startDelay, interval, TimeUnit.MILLISECONDS);
  }

  @Override
  public void stop() {
    scheduler.shutdown();
  }
}
