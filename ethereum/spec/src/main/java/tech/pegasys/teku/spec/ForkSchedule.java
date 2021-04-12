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

import static com.google.common.base.Preconditions.checkState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.util.ForkAndSpecMilestone;
import tech.pegasys.teku.ssz.type.Bytes4;

public class ForkSchedule {
  private final List<SpecMilestone> supportedMilestones;
  private final NavigableMap<UInt64, SpecMilestone> epochToMilestone;
  private final NavigableMap<UInt64, SpecMilestone> slotToMilestone;
  private final NavigableMap<UInt64, SpecMilestone> genesisOffsetToMilestone;
  private final Map<SpecMilestone, Fork> milestoneToFork;

  private ForkSchedule(
      final List<SpecMilestone> supportedMilestones,
      final NavigableMap<UInt64, SpecMilestone> epochToMilestone,
      final NavigableMap<UInt64, SpecMilestone> slotToMilestone,
      final NavigableMap<UInt64, SpecMilestone> genesisOffsetToMilestone,
      final Map<SpecMilestone, Fork> milestoneToFork) {
    this.supportedMilestones = supportedMilestones;
    this.epochToMilestone = epochToMilestone;
    this.slotToMilestone = slotToMilestone;
    this.genesisOffsetToMilestone = genesisOffsetToMilestone;
    this.milestoneToFork = milestoneToFork;
  }

  public int size() {
    return epochToMilestone.size();
  }

  /**
   * @return Milestones that are supported. Includes milestones that may be eclipsed by other
   *     milestones which are activated at the same time.
   */
  public Collection<SpecMilestone> getSupportedMilestones() {
    return supportedMilestones;
  }

  /** @return The last milestone scheduled to activate */
  public SpecMilestone getLastMilestone() {
    return epochToMilestone.lastEntry().getValue();
  }

  /**
   * @return Milestones that are actively transitioned to. Does not include milestones that are
   *     immediately eclipsed by later milestones that activate at the same slot.
   */
  public List<ForkAndSpecMilestone> getActiveMilestones() {
    return milestoneToFork.entrySet().stream()
        .map(entry -> new ForkAndSpecMilestone(entry.getValue(), entry.getKey()))
        .sorted(Comparator.comparing(f -> f.getFork().getEpoch()))
        .collect(Collectors.toList());
  }

  public Optional<Fork> getFork(final SpecMilestone milestone) {
    return Optional.ofNullable(milestoneToFork.get(milestone));
  }

  public Fork getFork(final UInt64 epoch) {
    return milestoneToFork.get(epochToMilestone.get(epoch));
  }

  public Optional<Fork> getNextFork(final UInt64 epoch) {
    return Optional.ofNullable(epochToMilestone.ceilingEntry(epoch.plus(1)))
        .map(Map.Entry::getValue)
        .map(milestoneToFork::get);
  }

  public List<Fork> getForks() {
    return epochToMilestone.values().stream()
        .map(milestoneToFork::get)
        .collect(Collectors.toList());
  }

  public SpecMilestone getSpecMilestoneAtEpoch(final UInt64 epoch) {
    return epochToMilestone.floorEntry(epoch).getValue();
  }

  public SpecMilestone getSpecMilestoneAtSlot(final UInt64 slot) {
    return slotToMilestone.floorEntry(slot).getValue();
  }

  public SpecMilestone getSpecMilestoneAtTime(final UInt64 genesisTime, final UInt64 currentTime) {
    final UInt64 genesisOffset = currentTime.minusMinZero(genesisTime);
    return genesisOffsetToMilestone.floorEntry(genesisOffset).getValue();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final ForkSchedule that = (ForkSchedule) o;
    return Objects.equals(slotToMilestone, that.slotToMilestone);
  }

  @Override
  public int hashCode() {
    return Objects.hash(slotToMilestone);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private final List<SpecMilestone> supportedMilestones = new ArrayList<>();
    private final NavigableMap<UInt64, SpecMilestone> epochToMilestone = new TreeMap<>();
    private final NavigableMap<UInt64, SpecMilestone> slotToMilestone = new TreeMap<>();
    private final NavigableMap<UInt64, SpecMilestone> genesisOffsetToMilestone = new TreeMap<>();
    private final Map<SpecMilestone, Fork> milestoneToFork = new HashMap<>();

    // Track info on the last processed milestone
    private Optional<Bytes4> prevForkVersion = Optional.empty();
    private UInt64 prevMilestoneForkEpoch = UInt64.ZERO;

    private Builder() {}

    public ForkSchedule build() {
      checkState(!epochToMilestone.isEmpty(), "Must configure at least one milestone");
      return new ForkSchedule(
          supportedMilestones,
          epochToMilestone,
          slotToMilestone,
          genesisOffsetToMilestone,
          milestoneToFork);
    }

    public Builder addNextMilestone(final SpecVersion spec) {
      processMilestone(spec);
      return this;
    }

    private void processMilestone(final SpecVersion spec) {
      final SpecMilestone milestone = spec.getMilestone();
      final Optional<UInt64> maybeForkSlot = SpecMilestone.getForkSlot(spec.getConfig(), milestone);
      final Optional<Bytes4> maybeForkVersion =
          SpecMilestone.getForkVersion(spec.getConfig(), milestone);
      if (maybeForkSlot.isEmpty() || maybeForkVersion.isEmpty()) {
        // This milestone is not enabled
        return;
      }

      // Current fork info
      final UInt64 forkSlot = maybeForkSlot.get();
      final Bytes4 forkVersion = maybeForkVersion.get();
      final UInt64 forkEpoch = spec.miscHelpers().computeEpochAtSlot(forkSlot);
      final UInt64 genesisOffset = spec.getForkChoiceUtil().getSlotStartTime(forkSlot, UInt64.ZERO);
      final Fork fork = new Fork(prevForkVersion.orElse(forkVersion), forkVersion, forkEpoch);

      // Validate against prev fork
      if (epochToMilestone.isEmpty() && !forkSlot.equals(UInt64.ZERO)) {
        throw new IllegalArgumentException("Must provide genesis milestone first.");
      }
      if (forkEpoch.isLessThan(prevMilestoneForkEpoch)) {
        final String msg =
            String.format(
                "Must provide milestones in order. Attempting to add milestone %s at epoch %s which is prior to the previously registered milestone at epoch %s",
                milestone, forkEpoch, prevMilestoneForkEpoch);
        throw new IllegalArgumentException(msg);
      }

      // Track milestone
      supportedMilestones.add(milestone);
      epochToMilestone.put(forkEpoch, milestone);
      slotToMilestone.put(forkSlot, milestone);
      genesisOffsetToMilestone.put(genesisOffset, milestone);
      milestoneToFork.put(milestone, fork);

      // Remember what we just processed
      prevMilestoneForkEpoch = forkEpoch;
      prevForkVersion = Optional.of(forkVersion);
    }
  }
}
