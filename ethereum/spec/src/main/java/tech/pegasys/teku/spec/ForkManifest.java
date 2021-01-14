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

package tech.pegasys.teku.spec;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import tech.pegasys.teku.datastructures.state.Fork;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.constants.SpecConstants;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;

public class ForkManifest {
  private final NavigableMap<UInt64, Fork> forkSchedule;

  private ForkManifest(final NavigableMap<UInt64, Fork> forkSchedule) {
    this.forkSchedule = forkSchedule;
  }

  public static ForkManifest create(final SpecConstants genesisConstants) {
    final NavigableMap<UInt64, Fork> schedule = new TreeMap<>();
    final Bytes4 genesisForkVersion = genesisConstants.getGenesisForkVersion();
    final UInt64 genesisEpoch = UInt64.valueOf(genesisConstants.getGenesisEpoch());
    schedule.put(genesisEpoch, new Fork(genesisForkVersion, genesisForkVersion, genesisEpoch));
    return new ForkManifest(schedule);
  }

  public static ForkManifest create(final List<Fork> forkList) {
    final NavigableMap<UInt64, Fork> schedule = new TreeMap<>();
    forkList.stream()
        .forEach(
            fork -> {
              if (!schedule.isEmpty()) {
                final Fork lastFork = schedule.lastEntry().getValue();
                checkArgument(lastFork.getEpoch().isLessThan(fork.getEpoch()));
                checkArgument(lastFork.getCurrent_version().equals(fork.getPrevious_version()));
                checkArgument(!fork.getPrevious_version().equals(fork.getCurrent_version()));
              }
              schedule.put(fork.getEpoch(), fork);
            });
    return new ForkManifest(schedule);
  }

  public Fork get(final UInt64 epoch) {
    return forkSchedule.floorEntry(epoch).getValue();
  }

  public Fork getGenesisFork() {
    return forkSchedule.firstEntry().getValue();
  }

  public List<Fork> getForkSchedule() {
    return new ArrayList<>(forkSchedule.values());
  }
}
