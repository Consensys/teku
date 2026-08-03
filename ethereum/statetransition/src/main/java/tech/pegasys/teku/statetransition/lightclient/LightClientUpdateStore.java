/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.statetransition.lightclient;

import java.util.concurrent.ConcurrentSkipListMap;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientUpdate;

public class LightClientUpdateStore {
//  private final ConcurrentSkipListMap<UInt64, LightClientUpdate> lightClientUpdateCache =
//      new ConcurrentSkipListMap<>();

  /** {@code is_better_update}. */
  public boolean isBetterUpdate(
      final LightClientUpdate newUpdate, final LightClientUpdate oldUpdate) {
    final int maxActiveParticipants = newUpdate.getSyncAggregate().getSyncCommitteeBits().size();
    final int newUpdateActiveParticipants =
        newUpdate.getSyncAggregate().getSyncCommitteeBits().getBitCount();
    final int oldUpdateActiveParticipants =
        oldUpdate.getSyncAggregate().getSyncCommitteeBits().getBitCount();

    boolean newUpdateHasSupermajority =
        newUpdateActiveParticipants * 3 >= maxActiveParticipants * 2;
    boolean oldUpdateHasSupermajority =
        oldUpdateActiveParticipants * 3 >= maxActiveParticipants * 2;

    if (newUpdateHasSupermajority != oldUpdateHasSupermajority) {
      return newUpdateHasSupermajority;
    }

    if (!newUpdateHasSupermajority && newUpdateActiveParticipants != oldUpdateActiveParticipants) {
      return newUpdateActiveParticipants > oldUpdateActiveParticipants;
    }

    return true;
  }
}
