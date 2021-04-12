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

import com.google.common.base.MoreObjects;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Holds information about a validators assignment to a sync committee in a more efficiently queried
 * form.
 */
public class SyncSubcommitteeAssignments {

  private final Map<Integer, Set<Integer>> subcommitteeToParticipationIndices;

  public SyncSubcommitteeAssignments(
      final Map<Integer, Set<Integer>> subcommitteeToParticipationIndices) {
    this.subcommitteeToParticipationIndices = subcommitteeToParticipationIndices;
  }

  public Set<Integer> getAssignedSubcommittees() {
    return Collections.unmodifiableSet(subcommitteeToParticipationIndices.keySet());
  }

  public Set<Integer> getParticipationBitIndices(final int subcommitteeIndex) {
    return Collections.unmodifiableSet(
        subcommitteeToParticipationIndices.getOrDefault(subcommitteeIndex, Collections.emptySet()));
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("subcommitteeToParticipationIndices", subcommitteeToParticipationIndices)
        .toString();
  }
}
