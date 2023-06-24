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

package tech.pegasys.teku.spec.config;

import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public interface NetworkingSpecConfig {

  int getGossipMaxSize();

  int getMaxChunkSize();

  int getMinEpochsForBlockRequests();

  // in seconds
  int getTtfbTimeout();

  // in seconds
  int getRespTimeout();

  UInt64 getAttestationPropagationSlotRange();

  // in millis
  int getMaximumGossipClockDisparity();

  // 4-byte domain for gossip message-id isolation of *invalid* snappy messages
  Bytes4 getMessageDomainInvalidSnappy();

  // 4-byte domain for gossip message-id isolation of *valid* snappy messages
  Bytes4 getMessageDomainValidSnappy();

  default NetworkingSpecConfig getNetworkingConfig() {
    return this;
  }
}
