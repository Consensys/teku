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

package org.ethereum.beacon.discovery.message.handler;

import org.ethereum.beacon.discovery.message.PongMessage;
import org.ethereum.beacon.discovery.schema.NodeSession;
import org.ethereum.beacon.discovery.task.TaskType;

public class PongHandler implements MessageHandler<PongMessage> {
  @Override
  public void handle(PongMessage message, NodeSession session) {
    session.clearRequestId(message.getRequestId(), TaskType.PING);
  }
}
