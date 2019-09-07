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

package tech.pegasys.artemis.datastructures.networking.hobbits.rpc;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigInteger;

/** Payload of status message */
public final class GetStatusMessage {

  private final byte[] userAgent;
  private final BigInteger timestamp;

  @JsonCreator
  public GetStatusMessage(
      @JsonProperty("user_agent") byte[] userAgent,
      @JsonProperty("timestamp") BigInteger timestamp) {
    this.userAgent = userAgent;
    this.timestamp = timestamp;
  }

  @JsonProperty("user_agent")
  public byte[] userAgent() {
    return userAgent;
  }

  @JsonProperty("timestamp")
  public BigInteger timestamp() {
    return timestamp;
  }
}
