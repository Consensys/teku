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

package tech.pegasys.teku.services.timer;

import static org.quartz.JobBuilder.newJob;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static org.quartz.TriggerBuilder.newTrigger;
import static tech.pegasys.teku.util.config.Constants.TIME_TICKER_REFRESH_RATE;

import java.util.concurrent.atomic.AtomicInteger;
import org.quartz.DateBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerFactory;
import org.quartz.SimpleTrigger;
import org.quartz.impl.StdSchedulerFactory;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;

public class TimerService extends Service {

  public static final String TIME_EVENTS_CHANNEL = "TimeEventsChannel";

  private static final AtomicInteger TIMER_ID_GENERATOR = new AtomicInteger();
  private static final AtomicInteger TIMER_TRIGGER_ID_GENERATOR = new AtomicInteger();

  private final Scheduler sched;
  private final JobDetail job;
  private static int START_DELAY = 0;
  private int interval;

  public TimerService(ServiceConfig config) {
    SchedulerFactory sf = new StdSchedulerFactory();
    this.interval = (int) ((1.0 / TIME_TICKER_REFRESH_RATE) * 1000); // Tick interval
    try {
      sched = sf.getScheduler();
      job =
          newJob(ScheduledTimeEvent.class)
              .withIdentity("Timer-" + TIMER_ID_GENERATOR.incrementAndGet())
              .build();
      job.getJobDataMap()
          .put(TIME_EVENTS_CHANNEL, config.getEventChannels().getPublisher(TimeTickChannel.class));

    } catch (SchedulerException e) {
      throw new IllegalArgumentException("TimerService failed to initialize", e);
    }
  }

  @Override
  public SafeFuture<?> doStart() {
    try {
      SimpleTrigger trigger =
          newTrigger()
              .withIdentity("TimerTrigger-" + TIMER_TRIGGER_ID_GENERATOR.incrementAndGet())
              .startAt(DateBuilder.futureDate(START_DELAY, DateBuilder.IntervalUnit.MILLISECOND))
              .withSchedule(simpleSchedule().withIntervalInMilliseconds(interval).repeatForever())
              .build();
      sched.scheduleJob(job, trigger);
      sched.start();
      return SafeFuture.COMPLETE;
    } catch (SchedulerException e) {
      return SafeFuture.failedFuture(new RuntimeException("TimerService failed to start", e));
    }
  }

  @Override
  public SafeFuture<?> doStop() {
    try {
      sched.shutdown();
      return SafeFuture.COMPLETE;
    } catch (SchedulerException e) {
      return SafeFuture.failedFuture(new RuntimeException("TimerService failed to stop", e));
    }
  }
}
