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

package tech.pegasys.teku.spec.logic.common.util;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.spec.constants.NetworkConstants.SYNC_COMMITTEE_SUBNET_COUNT;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateSchemaAltair;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.datastructures.util.SyncSubcommitteeAssignments;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.ssz.SszList;

class SyncCommitteeUtilTest {
  private final Spec spec = TestSpecFactory.createMinimalAltair();
  private final SpecConfigAltair config = SpecConfigAltair.required(spec.getGenesisSpecConfig());
  private final BeaconStateSchemaAltair stateSchema =
      BeaconStateSchemaAltair.required(spec.getGenesisSchemaDefinitions().getBeaconStateSchema());
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SszList<Validator> validators =
      dataStructureUtil.randomSszList(
          dataStructureUtil.getBeaconStateSchema().getValidatorsSchema(),
          config.getSyncCommitteeSize(),
          dataStructureUtil::randomValidator);
  private final List<SszPublicKey> validatorPublicKeys =
      validators.stream().map(Validator::getPubkeyBytes).map(SszPublicKey::new).collect(toList());
  private final int subcommitteeSize = config.getSyncCommitteeSize() / SYNC_COMMITTEE_SUBNET_COUNT;

  private final SyncCommitteeUtil syncCommitteeUtil =
      spec.getSyncCommitteeUtil(UInt64.ZERO).orElseThrow();

  @Test
  void getSyncSubCommittees_shouldCalculateSubcommitteeAssignments() {
    final BeaconState state = createStateWithCurrentSyncCommittee(validatorPublicKeys);

    final Map<UInt64, SyncSubcommitteeAssignments> syncSubcommittees =
        syncCommitteeUtil.getSyncSubcommittees(state, spec.getCurrentEpoch(state));

    // We explicitly created the committee to have every validator once in order so all validators
    // should be present with a single subcommittee assignment
    IntStream.range(0, validators.size())
        .forEach(
            index -> {
              final UInt64 validatorIndex = UInt64.valueOf(index);
              assertThat(syncSubcommittees).containsKey(validatorIndex);
              final SyncSubcommitteeAssignments assignments = syncSubcommittees.get(validatorIndex);
              final int expectedSubcommittee = index / subcommitteeSize;
              assertThat(assignments.getAssignedSubcommittees())
                  .describedAs("validator %d assigned subcommittees", index)
                  .containsExactly(expectedSubcommittee);
              assertThat(assignments.getParticipationBitIndices(expectedSubcommittee))
                  .containsExactly(index - (expectedSubcommittee * subcommitteeSize));
            });
  }

  @Test
  void getSyncSubCommittees_shouldSupportValidatorsInMultipleSubCommittees() {
    final List<SszPublicKey> singleSubcommittee = validatorPublicKeys.subList(0, subcommitteeSize);
    final List<SszPublicKey> syncCommittee = new ArrayList<>();
    for (int i = 0; i < SYNC_COMMITTEE_SUBNET_COUNT; i++) {
      syncCommittee.addAll(singleSubcommittee);
    }
    final List<UInt64> expectedValidatorIndices =
        IntStream.range(0, subcommitteeSize).mapToObj(UInt64::valueOf).collect(toList());

    final BeaconState state = createStateWithCurrentSyncCommittee(syncCommittee);
    final Map<UInt64, SyncSubcommitteeAssignments> syncSubcommittees =
        syncCommitteeUtil.getSyncSubcommittees(state, spec.getCurrentEpoch(state));

    assertThat(syncSubcommittees).containsOnlyKeys(expectedValidatorIndices);
    expectedValidatorIndices.forEach(
        index -> {
          final SyncSubcommitteeAssignments assignments = syncSubcommittees.get(index);
          assertThat(assignments.getAssignedSubcommittees())
              .containsExactlyInAnyOrderElementsOf(
                  IntStream.range(0, SYNC_COMMITTEE_SUBNET_COUNT).boxed().collect(toList()));
          IntStream.range(0, SYNC_COMMITTEE_SUBNET_COUNT)
              .forEach(
                  subcommitteeIndex ->
                      assertThat(assignments.getParticipationBitIndices(subcommitteeIndex))
                          .containsExactly(index.intValue()));
        });
  }

  @Test
  void getSyncSubCommittees_shouldSupportValidatorsInTheSameSubCommitteeMultipleTimes() {
    final List<SszPublicKey> syncCommittee = new ArrayList<>(validatorPublicKeys);
    final SszPublicKey validator0 = syncCommittee.get(0);
    syncCommittee.set(2, validator0);
    syncCommittee.set(3, validator0);

    final BeaconState state = createStateWithCurrentSyncCommittee(syncCommittee);
    final Map<UInt64, SyncSubcommitteeAssignments> syncSubcommittees =
        syncCommitteeUtil.getSyncSubcommittees(state, spec.getCurrentEpoch(state));

    final SyncSubcommitteeAssignments assignments = syncSubcommittees.get(UInt64.ZERO);
    assertThat(assignments.getAssignedSubcommittees()).containsExactly(0);
    assertThat(assignments.getParticipationBitIndices(0)).containsExactlyInAnyOrder(0, 2, 3);
  }

  private BeaconState createStateWithCurrentSyncCommittee(
      final List<SszPublicKey> committeePublicKeys) {
    return dataStructureUtil
        .stateBuilderAltair()
        .validators(validators)
        .currentSyncCommittee(
            stateSchema
                .getCurrentSyncCommitteeSchema()
                .create(committeePublicKeys, Collections.emptyList()))
        .build();
  }
}
