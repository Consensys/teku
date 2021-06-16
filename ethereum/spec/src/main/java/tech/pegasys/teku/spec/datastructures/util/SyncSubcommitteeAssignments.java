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

package tech.pegasys.teku.spec.datastructures.util;

import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;

import com.google.common.base.MoreObjects;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Holds information about a validators assignment to a sync committee in a more efficiently queried
 * form.
 */
public class SyncSubcommitteeAssignments {

  public static final SyncSubcommitteeAssignments NONE =
      new SyncSubcommitteeAssignments(emptyMap(), emptySet());

  private final Map<Integer, Set<Integer>> subcommitteeToParticipationIndices;
  private final Set<Integer> committeeIndices;

  private SyncSubcommitteeAssignments(
      final Map<Integer, Set<Integer>> subcommitteeToParticipationIndices,
      final Set<Integer> committeeIndices) {
    this.subcommitteeToParticipationIndices = subcommitteeToParticipationIndices;
    this.committeeIndices = committeeIndices;
  }

  public static SyncSubcommitteeAssignments.Builder builder() {
    return new SyncSubcommitteeAssignments.Builder();
  }

  public Set<Integer> getAssignedSubcommittees() {
    return Collections.unmodifiableSet(subcommitteeToParticipationIndices.keySet());
  }

  public Set<Integer> getCommitteeIndices() {
    return committeeIndices;
  }

  public Set<Integer> getParticipationBitIndices(final int subcommitteeIndex) {
    return Collections.unmodifiableSet(
        subcommitteeToParticipationIndices.getOrDefault(subcommitteeIndex, Collections.emptySet()));
  }

  public boolean isEmpty() {
    return subcommitteeToParticipationIndices.isEmpty();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("subcommitteeToParticipationIndices", subcommitteeToParticipationIndices)
        .toString();
  }

  public static class Builder {
    private final Map<Integer, Set<Integer>> subcommitteeToParticipationIndices = new HashMap<>();
    private final Set<Integer> committeeIndices = new HashSet<>();

    public Builder addAssignment(
        final int subcommitteeIndex, final int subcommitteeParticipationIndex) {
      subcommitteeToParticipationIndices
          .computeIfAbsent(subcommitteeIndex, __ -> new HashSet<>())
          .add(subcommitteeParticipationIndex);
      return this;
    }

    public Builder addCommitteeIndex(final Integer index) {
      committeeIndices.add(index);
      return this;
    }

    public SyncSubcommitteeAssignments build() {
      return new SyncSubcommitteeAssignments(subcommitteeToParticipationIndices, committeeIndices);
    }
  }
}
