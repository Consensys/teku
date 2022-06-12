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

import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobListener;
import org.quartz.TriggerListener;

public class ScheduledTimeEvent implements Job {

  /**
   * Called by the <code>{@link org.quartz.Scheduler}</code> when a <code>{@link org.quartz.Trigger}
   * </code> fires that is associated with the <code>Job</code>.
   *
   * <p>The implementation may wish to set a {@link JobExecutionContext#setResult(Object) result}
   * object on the {@link JobExecutionContext} before this method exits. The result itself is
   * meaningless to Quartz, but may be informative to <code>{@link JobListener}s</code> or <code>
   * {@link TriggerListener}s</code> that are watching the job's execution.
   *
   * @param context the job execution context
   */
  @Override
  public void execute(JobExecutionContext context) {
    JobDataMap data = context.getJobDetail().getJobDataMap();
    TimeTickHandler timeTickHandler = (TimeTickHandler) data.get(TimerService.TICK_HANDLER);
    timeTickHandler.onTick();
  }
}
