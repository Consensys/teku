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

package tech.pegasys.teku.validator.remote;

import java.util.Objects;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class BeaconChainEvent {

  public static final String ATTESTATION = "BroadcastAttestationEvent";
  public static final String AGGREGATION = "BroadcastAggregatesEvent";
  public static final String IMPORTED_BLOCK = "ImportedBlockEvent";
  public static final String ON_SLOT = "OnSlotEvent";
  public static final String REORG_OCCURRED = "ReorgOccurredEvent";

  private String name;
  private UInt64 data;

  public BeaconChainEvent(final String name, final UInt64 data) {
    this.name = name;
    this.data = data;
  }

  public BeaconChainEvent() {}

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public UInt64 getData() {
    return data;
  }

  public void setData(final UInt64 data) {
    this.data = data;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final BeaconChainEvent that = (BeaconChainEvent) o;
    return name.equals(that.name) && data.equals(that.data);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, data);
  }
}
