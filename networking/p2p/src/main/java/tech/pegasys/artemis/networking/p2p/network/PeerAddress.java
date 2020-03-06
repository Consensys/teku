/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.artemis.networking.p2p.network;

import java.util.Objects;
import tech.pegasys.artemis.networking.p2p.peer.NodeId;
import tech.pegasys.artemis.util.async.SafeFuture;

public class PeerAddress {
  private final NodeId id;

  public PeerAddress(final NodeId id) {
    this.id = id;
  }

  public NodeId getId() {
    return id;
  }

  @SuppressWarnings("unchecked")
  public <T> SafeFuture<T> as(final Class<T> clazz) {
    if (clazz.isInstance(this)) {
      return SafeFuture.completedFuture((T) this);
    } else {
      return SafeFuture.failedFuture(
          new IllegalArgumentException("Unsupported static peer type: " + getClass().getName()));
    }
  }

  @Override
  public String toString() {
    return id.toString();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final PeerAddress that = (PeerAddress) o;
    return id.equals(that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
