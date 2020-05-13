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

package tech.pegasys.teku.storage.api;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;

public class TrackingReorgEventChannel implements ReorgEventChannel {
  private final List<ReorgEvent> reorgEvents = new ArrayList<>();

  @Override
  public void reorgOccurred(final Bytes32 bestBlockRoot, final UnsignedLong bestSlot) {
    reorgEvents.add(new ReorgEvent(bestBlockRoot, bestSlot));
  }

  public List<ReorgEvent> getReorgEvents() {
    return reorgEvents;
  }

  public static class ReorgEvent {
    private final Bytes32 bestBlockRoot;
    private final UnsignedLong bestSlot;

    public ReorgEvent(final Bytes32 bestBlockRoot, final UnsignedLong bestSlot) {
      this.bestBlockRoot = bestBlockRoot;
      this.bestSlot = bestSlot;
    }

    public Bytes32 getBestBlockRoot() {
      return bestBlockRoot;
    }

    public UnsignedLong getBestSlot() {
      return bestSlot;
    }
  }
}
