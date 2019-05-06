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
import java.util.Date;
import java.util.UUID;
import org.quartz.DateBuilder;
import org.quartz.Job;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerFactory;
import org.quartz.SimpleTrigger;
import org.quartz.impl.StdSchedulerFactory;

public class QuartzTimer<T extends Job> implements Timer {
  final Class<T> type;
  final Scheduler sched;
  final SimpleTrigger trigger;
  final JobDetail job;

  public QuartzTimer(Class<T> type, EventBus eventBus, Date startTime, int interval)
      throws IllegalArgumentException {
    SchedulerFactory sf = new StdSchedulerFactory();
    try {
      this.type = type;
      sched = sf.getScheduler();
      UUID uuid = UUID.randomUUID();
      job = newJob(type).withIdentity(type.getSimpleName() + uuid.toString(), "group").build();
      job.getJobDataMap().put(EventBus.class.getSimpleName(), eventBus);
      trigger =
          newTrigger()
              .withIdentity("trigger-" + type.getSimpleName() + uuid.toString(), "group")
              .startAt(startTime)
              .withSchedule(simpleSchedule().withIntervalInSeconds(interval).repeatForever())
              .build();
      sched.scheduleJob(job, trigger);
    } catch (SchedulerException e) {
      throw new IllegalArgumentException(
          "In QuartzTimer a SchedulerException was thrown: " + e.toString());
    }
  }

  public QuartzTimer(Class<T> task, EventBus eventBus, int startDelay, int interval) {
    this(task, eventBus, DateBuilder.nextGivenSecondDate(null, startDelay), interval);
  }

  @Override
  public void schedule() throws IllegalArgumentException {
    try {
      sched.start();
    } catch (SchedulerException e) {
      throw new IllegalArgumentException(
          "In QuartzTimer a SchedulerException was thrown: " + e.toString());
    }
  }
}
