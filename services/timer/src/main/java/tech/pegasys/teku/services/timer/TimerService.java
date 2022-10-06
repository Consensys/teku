/*
 * Copyright ConsenSys Software Inc., 2022
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerFactory;
import org.quartz.SimpleTrigger;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.simpl.RAMJobStore;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.service.serviceutils.Service;

public class TimerService extends Service {

  public static final double TIME_TICKER_REFRESH_RATE = 2; // per sec
  public static final String TICK_HANDLER = "TickHandler";

  private static final AtomicInteger TIMER_ID_GENERATOR = new AtomicInteger();
  private static final AtomicInteger TIMER_TRIGGER_ID_GENERATOR = new AtomicInteger();

  private final Scheduler sched;
  private final JobDetail job;
  // Tick interval
  private final int intervalMs = (int) ((1.0 / TIME_TICKER_REFRESH_RATE) * 1000);

  public TimerService(final TimeTickHandler timeTickHandler) {
    final SchedulerFactory sf = createSchedulerFactory();
    try {
      sched = sf.getScheduler();
      job =
          newJob(ScheduledTimeEvent.class)
              .withIdentity("Timer-" + TIMER_ID_GENERATOR.incrementAndGet())
              .build();
      job.getJobDataMap().put(TICK_HANDLER, timeTickHandler);

    } catch (SchedulerException e) {
      throw new IllegalArgumentException("TimerService failed to initialize", e);
    }
  }

  private StdSchedulerFactory createSchedulerFactory() {
    try {
      final Properties properties = new Properties();
      properties.put("org.quartz.threadPool.threadCount", "1");
      properties.put("org.quartz.threadPool.threadNamePrefix", "TimeTick");
      properties.put("org.quartz.jobStore.class", RAMJobStore.class.getName());
      properties.put("org.quartz.scheduler.skipUpdateCheck", "true");
      // If job doesn't fire by the time the next one is due, skip it entirely
      properties.put("org.quartz.jobStore.misfireThreshold", Integer.toString(intervalMs));
      return new StdSchedulerFactory(properties);
    } catch (final SchedulerException e) {
      throw new IllegalStateException("Failed to configure timer", e);
    }
  }

  @Override
  public SafeFuture<?> doStart() {
    try {
      SimpleTrigger trigger =
          newTrigger()
              .withIdentity("TimerTrigger-" + TIMER_TRIGGER_ID_GENERATOR.incrementAndGet())
              .withSchedule(
                  simpleSchedule()
                      .withIntervalInMilliseconds(intervalMs)
                      // If a scheduled event fails (including if it is delayed too much by resource
                      // contention), then just skip it and fire the next event when it is due
                      .withMisfireHandlingInstructionNextWithRemainingCount()
                      .repeatForever())
              .startAt(Date.from(Instant.now().truncatedTo(ChronoUnit.SECONDS).plusSeconds(1)))
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
