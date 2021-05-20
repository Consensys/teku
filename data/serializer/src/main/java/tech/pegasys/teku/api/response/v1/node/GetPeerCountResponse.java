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

import java.util.List;
import java.util.Objects;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class GetPeerCountResponse {
  public final PeerCount data;

  public GetPeerCountResponse(final PeerCount data) {
    this.data = data;
  }

  public static GetPeerCountResponse create(final List<Peer> peers) {
    int disconnected = 0;
    int connecting = 0;
    int connected = 0;
    int disconnecting = 0;

    for (Peer peer : peers) {
      switch (peer.state) {
        case disconnected:
          disconnected++;
          break;
        case connecting:
          connecting++;
          break;
        case connected:
          connected++;
          break;
        case disconnecting:
          disconnecting++;
          break;
        default:
          break;
      }
    }

    return new GetPeerCountResponse(
        new PeerCount(
            UInt64.valueOf(disconnected),
            UInt64.valueOf(connecting),
            UInt64.valueOf(connected),
            UInt64.valueOf(disconnecting)));
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final GetPeerCountResponse that = (GetPeerCountResponse) o;
    return Objects.equals(data, that.data);
  }

  @Override
  public int hashCode() {
    return Objects.hash(data);
  }
}
