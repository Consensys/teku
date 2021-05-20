/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.api.response.v1.node;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class PeerCount {
  @Schema(type = "string", format = "uint64")
  public final UInt64 disconnected;

  @Schema(type = "string", format = "uint64")
  public final UInt64 connecting;

  @Schema(type = "string", format = "uint64")
  public final UInt64 connected;

  @Schema(type = "string", format = "uint64")
  public final UInt64 disconnecting;

  @JsonCreator
  public PeerCount(
      @JsonProperty("disconnected") final UInt64 disconnected,
      @JsonProperty("connecting") final UInt64 connecting,
      @JsonProperty("connected") final UInt64 connected,
      @JsonProperty("disconnecting") final UInt64 disconnecting) {
    this.disconnected = disconnected;
    this.connecting = connecting;
    this.connected = connected;
    this.disconnecting = disconnecting;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("disconnected", disconnected)
        .add("connecting", connecting)
        .add("connected", connected)
        .add("disconnecting", disconnecting)
        .toString();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final PeerCount peerCount = (PeerCount) o;
    return Objects.equals(disconnected, peerCount.disconnected)
        && Objects.equals(connecting, peerCount.connecting)
        && Objects.equals(connected, peerCount.connected)
        && Objects.equals(disconnecting, peerCount.disconnecting);
  }

  @Override
  public int hashCode() {
    return Objects.hash(disconnected, connecting, connected, disconnecting);
  }
}
