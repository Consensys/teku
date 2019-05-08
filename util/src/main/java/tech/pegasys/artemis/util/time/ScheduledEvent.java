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
import java.util.Date;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

public class ScheduledEvent implements Runnable, Job {

  private EventBus eventBus;

  public ScheduledEvent() {}

  public ScheduledEvent(EventBus eventBus) {
    this.eventBus = eventBus;
  }

  /**
   * When an object implementing interface <code>Runnable</code> is used to create a thread,
   * starting the thread causes the object's <code>run</code> method to be called in that separately
   * executing thread.
   *
   * <p>The general contract of the method <code>run</code> is that it may take any action
   * whatsoever.
   *
   * @see Thread#run()
   */
  @Override
  public void run() {
    this.eventBus.post(new Date());
  }

  /**
   * Called by the <code>{@link Scheduler}</code> when a <code>{@link Trigger}</code> fires that is
   * associated with the <code>Job</code>.
   *
   * <p>The implementation may wish to set a {@link JobExecutionContext#setResult(Object) result}
   * object on the {@link JobExecutionContext} before this method exits. The result itself is
   * meaningless to Quartz, but may be informative to <code>{@link JobListener}s</code> or <code>
   * {@link TriggerListener}s</code> that are watching the job's execution.
   *
   * @param context
   * @throws JobExecutionException if there is an exception while executing the job.
   */
  @Override
  public void execute(JobExecutionContext context) throws JobExecutionException {
    JobDataMap data = context.getJobDetail().getJobDataMap();
    this.eventBus = (EventBus) data.get(EventBus.class.getSimpleName());
    this.eventBus.post(new Date());
  }
}
