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

package tech.pegasys.teku.spec.logic.versions.heze.helpers;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigHeze;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateAccessorsGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.MiscHelpersGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.PredicatesGloas;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

public class BeaconStateAccessorsHeze extends BeaconStateAccessorsGloas {

  private final SpecConfigHeze configHeze;

  public BeaconStateAccessorsHeze(
      final SpecConfigHeze config,
      final SchemaDefinitionsGloas schemaDefinitions,
      final PredicatesGloas predicates,
      final MiscHelpersGloas miscHelpers) {
    super(config, schemaDefinitions, predicates, miscHelpers);
    this.configHeze = config;
  }

  /**
   * Returns the inclusion list committee for a given slot per spec: <code>
   * def get_inclusion_list_committee(state: BeaconState, slot: Slot) -> Vector[ValidatorIndex, INCLUSION_LIST_COMMITTEE_SIZE]:
   *     epoch = compute_epoch_at_slot(slot)
   *     indices = []
   *     committees_per_slot = get_committee_count_per_slot(state, epoch)
   *     for i in range(committees_per_slot):
   *         committee = get_beacon_committee(state, slot, CommitteeIndex(i))
   *         indices.extend(committee)
   *     return Vector[ValidatorIndex, INCLUSION_LIST_COMMITTEE_SIZE](
   *         [indices[i % len(indices)] for i in range(INCLUSION_LIST_COMMITTEE_SIZE)]
   *     )
   * </code>
   */
  public IntList getInclusionListCommittee(final BeaconState state, final UInt64 slot) {
    final UInt64 epoch = miscHelpers.computeEpochAtSlot(slot);
    final IntList indices = new IntArrayList();
    final int committeesPerSlot = getCommitteeCountPerSlot(state, epoch).intValue();
    for (int i = 0; i < committeesPerSlot; i++) {
      final IntList committee = getBeaconCommittee(state, slot, UInt64.valueOf(i));
      indices.addAll(committee);
    }
    final int size = indices.size();
    final IntList inclusionListCommittee = new IntArrayList();
    final int committeeSize = configHeze.getInclusionListCommitteeSize();
    for (int i = 0; i < committeeSize; i++) {
      inclusionListCommittee.add(indices.getInt(i % size));
    }
    return inclusionListCommittee;
  }
}
