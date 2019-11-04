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

package org.ethereum.beacon.discovery.network;

import org.ethereum.beacon.discovery.packet.Packet;
import org.ethereum.beacon.discovery.schema.NodeRecord;

/**
 * Abstraction on the top of the {@link Packet}.
 *
 * <p>Stores `packet` and associated node record. Record could be a sender or recipient, depends on
 * session.
 */
public interface NetworkParcel {
  Packet getPacket();

  NodeRecord getNodeRecord();
}
