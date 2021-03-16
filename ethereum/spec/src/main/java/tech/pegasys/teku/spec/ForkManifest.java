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
import static tech.pegasys.teku.spec.constants.SpecConstants.GENESIS_EPOCH;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.constants.SpecConstants;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.ssz.type.Bytes4;

public class ForkManifest {
  private final NavigableMap<UInt64, Fork> forkSchedule = new TreeMap<>();

  private ForkManifest(final Collection<Fork> forks) {
    validateAndBuildSchedule(forks);
  }

  private void validateAndBuildSchedule(final Collection<Fork> forks) {
    checkArgument(!forks.isEmpty(), "Fork schedule must contain a genesis fork");
    forks.stream()
        .sorted(Comparator.comparing(Fork::getEpoch))
        .forEach(
            (fork) -> {
              if (forkSchedule.isEmpty()) {
                checkArgument(fork.getEpoch().isZero(), "Genesis fork must start at epoch 0");
                checkArgument(
                    fork.getPrevious_version().equals(fork.getCurrent_version()),
                    "Genesis fork previous and current version must match");
              } else {
                final Fork lastFork = forkSchedule.lastEntry().getValue();
                checkArgument(
                    lastFork.getEpoch().isLessThan(fork.getEpoch()),
                    "Fork schedule must be in ascending order of epoch");
                checkArgument(
                    lastFork.getCurrent_version().equals(fork.getPrevious_version()),
                    "Previous version of a fork must match the previous fork");
                checkArgument(
                    !fork.getPrevious_version().equals(fork.getCurrent_version()),
                    "Previous and current version of non genesis forks must differ");
              }
              forkSchedule.put(fork.getEpoch(), fork);
            });
  }

  public static ForkManifest create(final SpecConstants genesisConstants) {
    final Bytes4 genesisForkVersion = genesisConstants.getGenesisForkVersion();
    return new ForkManifest(
        List.of(new Fork(genesisForkVersion, genesisForkVersion, GENESIS_EPOCH)));
  }

  public static ForkManifest create(final List<Fork> forkList) {
    return new ForkManifest(forkList);
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

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final ForkManifest that = (ForkManifest) o;
    return Objects.equals(forkSchedule, that.forkSchedule);
  }

  @Override
  public int hashCode() {
    return Objects.hash(forkSchedule);
  }
}
