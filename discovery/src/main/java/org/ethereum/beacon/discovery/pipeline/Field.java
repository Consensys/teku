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

package org.ethereum.beacon.discovery.pipeline;

public enum Field {
  SESSION_LOOKUP, // Node id, requests session lookup
  SESSION, // Node session
  INCOMING, // Raw incoming data
  PACKET_UNKNOWN, // Unknown packet
  PACKET_WHOAREYOU, // WhoAreYou packet
  PACKET_AUTH_HEADER_MESSAGE, // Auth header message packet
  PACKET_MESSAGE, // Standard message packet
  MESSAGE, // Message extracted from the packet
  NODE, // Sender/recipient node
  BAD_PACKET, // Bad, rejected packet
  BAD_MESSAGE, // Bad, rejected message
  BAD_EXCEPTION, // Stores exception for bad packet or message
  TASK, // Task to perform
  FUTURE, // Completable future
}
