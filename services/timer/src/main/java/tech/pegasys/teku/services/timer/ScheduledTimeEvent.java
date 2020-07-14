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

import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import tech.pegasys.teku.util.time.channels.TimeTickChannel;

public class ScheduledTimeEvent implements Job {

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
    TimeTickChannel timeTickChannel = (TimeTickChannel) data.get(TimerService.TIME_EVENTS_CHANNEL);
    timeTickChannel.onTick();
  }
}
