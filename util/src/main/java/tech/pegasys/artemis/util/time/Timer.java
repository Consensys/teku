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

import static org.quartz.JobBuilder.newJob;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static org.quartz.TriggerBuilder.newTrigger;

import com.google.common.eventbus.EventBus;
import java.util.UUID;
import org.quartz.DateBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerFactory;
import org.quartz.SimpleTrigger;
import org.quartz.impl.StdSchedulerFactory;

public class Timer {
  private final Scheduler sched;
  private final JobDetail job;
  private final UUID uuid;
  private int startDelay;
  private int interval;

  public Timer(EventBus eventBus, Integer startDelay, Integer interval)
      throws IllegalArgumentException {
    SchedulerFactory sf = new StdSchedulerFactory();
    this.startDelay = startDelay;
    this.interval = interval;
    try {
      sched = sf.getScheduler();
      uuid = UUID.randomUUID();
      job =
          newJob(ScheduledEvent.class)
              .withIdentity(EventBus.class.getSimpleName() + uuid.toString(), "group")
              .build();
      job.getJobDataMap().put(EventBus.class.getSimpleName(), eventBus);

    } catch (SchedulerException e) {
      throw new IllegalArgumentException(
          "In QuartzTimer a SchedulerException was thrown: " + e.toString());
    }
  }

  public void start() throws IllegalArgumentException {
    try {
      SimpleTrigger trigger =
          newTrigger()
              .withIdentity("trigger-" + EventBus.class.getSimpleName() + uuid.toString(), "group")
              .startAt(DateBuilder.futureDate(startDelay, DateBuilder.IntervalUnit.MILLISECOND))
              .withSchedule(simpleSchedule().withIntervalInMilliseconds(interval).repeatForever())
              .build();
      sched.scheduleJob(job, trigger);
      sched.start();
    } catch (SchedulerException e) {
      throw new IllegalArgumentException(
          "In QuartzTimer a SchedulerException was thrown: " + e.toString());
    }
  }

  public void stop() {
    try {
      sched.shutdown();
    } catch (SchedulerException e) {
      throw new IllegalArgumentException(
          "In QuartzTimer a SchedulerException was thrown: " + e.toString());
    }
  }
}
