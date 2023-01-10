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

package tech.pegasys.teku.services.executionlayer;

import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.ethereum.executionlayer.ExecutionLayerBlockManagerImpl;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerBlockManager;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;

public class ExecutionLayerBlockManagerFactory {
  public static ExecutionLayerBlockManager create(
      final ExecutionLayerChannel executionLayerManager, final EventChannels eventChannels) {
    final ExecutionLayerBlockManagerImpl executionLayerInitiator =
        new ExecutionLayerBlockManagerImpl(executionLayerManager);
    eventChannels.subscribe(SlotEventsChannel.class, executionLayerInitiator);
    return executionLayerInitiator;
  }

  public static ExecutionLayerBlockManager createDummy() {
    return ExecutionLayerBlockManager.NOOP;
  }
}
