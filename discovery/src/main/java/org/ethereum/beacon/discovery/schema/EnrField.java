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

package org.ethereum.beacon.discovery.schema;

/** Fields of Ethereum Node Record */
public interface EnrField {
  // Schema id
  String ID = "id";
  // IPv4 address
  String IP_V4 = "ip";
  // TCP port, integer
  String TCP_V4 = "tcp";
  // UDP port, integer
  String UDP_V4 = "udp";
  // IPv6 address
  String IP_V6 = "ip6";
  // IPv6-specific TCP port
  String TCP_V6 = "tcp6";
  // IPv6-specific UDP port
  String UDP_V6 = "udp6";
}
