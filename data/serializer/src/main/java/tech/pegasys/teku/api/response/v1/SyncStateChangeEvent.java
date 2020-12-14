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

package tech.pegasys.teku.api.response.v1;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

public class SyncStateChangeEvent {
  @JsonProperty("sync_state")
  public final String sync_state;

  @JsonCreator
  public SyncStateChangeEvent(@JsonProperty("sync_state") final String sync_state) {
    this.sync_state = sync_state;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SyncStateChangeEvent)) return false;
    SyncStateChangeEvent that = (SyncStateChangeEvent) o;
    return Objects.equals(sync_state, that.sync_state);
  }

  @Override
  public int hashCode() {
    return Objects.hash(sync_state);
  }

  @Override
  public String toString() {
    return "SyncStateEvent{" + "sync_state='" + sync_state + '\'' + '}';
  }
}
