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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientFinalityUpdate;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientOptimisticUpdate;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientUpdate;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@TestSpecContext(allMilestones = true, ignoredMilestones = SpecMilestone.PHASE0)
public class LightClientUpdateStoreTest {

  private Spec spec;
  private DataStructureUtil dataStructureUtil;
  private LightClientUpdateStore store;
  private UInt64 periodOneStartSlot;
  private int supermajorityParticipants;

  @BeforeEach
  void setUp(final SpecContext specContext) {
    spec = specContext.getSpec();
    dataStructureUtil = specContext.getDataStructureUtil();
    store = new LightClientUpdateStore(spec);

    final SyncCommitteeUtil syncCommitteeUtil = spec.getSyncCommitteeUtilRequired(UInt64.ZERO);
    periodOneStartSlot =
        spec.computeStartSlotAtEpoch(
            syncCommitteeUtil.computeFirstEpochOfNextSyncCommitteePeriod(UInt64.ZERO));
    final SpecConfigAltair config = SpecConfigAltair.required(spec.getGenesisSpecConfig());
    supermajorityParticipants = (config.getSyncCommitteeSize() * 2 + 2) / 3;
  }

  @TestTemplate
  public void addUpdate_shouldPreferSupermajority() {
    final LightClientUpdate withSupermajority =
        createLightClientUpdate().syncCommitteeParticipants(supermajorityParticipants).build();
    final LightClientUpdate belowSupermajority =
        createLightClientUpdate().syncCommitteeParticipants(supermajorityParticipants - 1).build();

    assertThat(getBestUpdateAfterAdding(withSupermajority, belowSupermajority))
        .isEqualTo(withSupermajority);
    assertThat(getBestUpdateAfterAdding(belowSupermajority, withSupermajority))
        .isEqualTo(withSupermajority);
  }

  @TestTemplate
  public void addUpdate_shouldPreferMoreParticipantsWhenNeitherHasSupermajority() {
    final LightClientUpdate more =
        createLightClientUpdate().syncCommitteeParticipants(supermajorityParticipants - 1).build();
    final LightClientUpdate fewer =
        createLightClientUpdate().syncCommitteeParticipants(supermajorityParticipants - 2).build();

    assertThat(getBestUpdateAfterAdding(more, fewer)).isEqualTo(more);
    assertThat(getBestUpdateAfterAdding(fewer, more)).isEqualTo(more);
  }

  @TestTemplate
  public void addUpdate_shouldNotCompareParticipationEarlyWhenBothHaveSupermajority() {
    final LightClientUpdate fewerWithSyncCommittee =
        createLightClientUpdate()
            .syncCommitteeParticipants(supermajorityParticipants)
            .syncCommitteeBranch(true)
            .build();
    final LightClientUpdate moreWithoutSyncCommittee =
        createLightClientUpdate().syncCommitteeBranch(false).build();

    assertThat(getBestUpdateAfterAdding(fewerWithSyncCommittee, moreWithoutSyncCommittee))
        .isEqualTo(fewerWithSyncCommittee);
    assertThat(getBestUpdateAfterAdding(moreWithoutSyncCommittee, fewerWithSyncCommittee))
        .isEqualTo(fewerWithSyncCommittee);
  }

  @TestTemplate
  public void addUpdate_shouldPreferRelevantSyncCommittee() {
    final LightClientUpdate withSyncCommittee =
        createLightClientUpdate().syncCommitteeBranch(true).build();
    final LightClientUpdate withoutSyncCommittee =
        createLightClientUpdate().syncCommitteeBranch(false).build();

    assertThat(getBestUpdateAfterAdding(withSyncCommittee, withoutSyncCommittee))
        .isEqualTo(withSyncCommittee);
    assertThat(getBestUpdateAfterAdding(withoutSyncCommittee, withSyncCommittee))
        .isEqualTo(withSyncCommittee);
  }

  @TestTemplate
  public void addUpdate_shouldPreferFinality() {
    final LightClientUpdate withFinality = createLightClientUpdate().finalityBranch(true).build();
    final LightClientUpdate withoutFinality =
        createLightClientUpdate().finalityBranch(false).build();

    assertThat(getBestUpdateAfterAdding(withFinality, withoutFinality)).isEqualTo(withFinality);
    assertThat(getBestUpdateAfterAdding(withoutFinality, withFinality)).isEqualTo(withFinality);
  }

  @TestTemplate
  public void addUpdate_shouldPreferSyncCommitteeFinality() {
    final LightClientUpdate finalizedInAttestedPeriod = createLightClientUpdate().build();
    final LightClientUpdate finalizedInEarlierPeriod =
        createLightClientUpdate().finalizedSlot(UInt64.ZERO).build();

    assertThat(getBestUpdateAfterAdding(finalizedInAttestedPeriod, finalizedInEarlierPeriod))
        .isEqualTo(finalizedInAttestedPeriod);
    assertThat(getBestUpdateAfterAdding(finalizedInEarlierPeriod, finalizedInAttestedPeriod))
        .isEqualTo(finalizedInAttestedPeriod);
  }

  @TestTemplate
  public void addUpdate_shouldSkipSyncCommitteeFinalityWhenNeitherHasFinality() {
    final LightClientUpdate moreParticipants =
        createLightClientUpdate().finalityBranch(false).finalizedSlot(UInt64.ZERO).build();
    final LightClientUpdate finalizedInAttestedPeriod =
        createLightClientUpdate()
            .finalityBranch(false)
            .syncCommitteeParticipants(supermajorityParticipants)
            .build();

    assertThat(getBestUpdateAfterAdding(moreParticipants, finalizedInAttestedPeriod))
        .isEqualTo(moreParticipants);
    assertThat(getBestUpdateAfterAdding(finalizedInAttestedPeriod, moreParticipants))
        .isEqualTo(moreParticipants);
  }

  @TestTemplate
  public void addUpdate_shouldPreferMoreParticipantsAsTiebreak() {
    final LightClientUpdate more = createLightClientUpdate().build();
    final LightClientUpdate fewer =
        createLightClientUpdate().syncCommitteeParticipants(supermajorityParticipants).build();

    assertThat(getBestUpdateAfterAdding(more, fewer)).isEqualTo(more);
    assertThat(getBestUpdateAfterAdding(fewer, more)).isEqualTo(more);
  }

  @TestTemplate
  public void addUpdate_shouldPreferOlderAttestedSlot() {
    final LightClientUpdate older = createLightClientUpdate().build();
    final LightClientUpdate newer =
        createLightClientUpdate().attestedSlot(periodOneStartSlot.increment()).build();

    assertThat(getBestUpdateAfterAdding(older, newer)).isEqualTo(older);
    assertThat(getBestUpdateAfterAdding(newer, older)).isEqualTo(older);
  }

  @TestTemplate
  public void addUpdate_shouldPreferOlderSignatureSlot() {
    final LightClientUpdate older = createLightClientUpdate().build();
    final LightClientUpdate newer =
        createLightClientUpdate().signatureSlot(periodOneStartSlot.increment()).build();

    assertThat(getBestUpdateAfterAdding(older, newer)).isEqualTo(older);
    assertThat(getBestUpdateAfterAdding(newer, older)).isEqualTo(older);
  }

  @TestTemplate
  public void addUpdate_shouldKeepExistingUpdateWhenIdentical() {
    // Every clause falls through to `signatureSlot < signatureSlot`, which must be false.
    final LightClientUpdate update = createLightClientUpdate().build();

    store.addUpdate(update);
    store.addUpdate(update);

    assertThat(store.getBestUpdatesInRange(UInt64.ONE, 1)).containsExactly(update);
  }

  @TestTemplate
  public void addUpdate_shouldRejectUpdateAttestedAndSignedInDifferentPeriods() {
    final LightClientUpdate update =
        createLightClientUpdate().signatureSlot(periodOneStartSlot.times(2)).build();

    store.addUpdate(update);

    assertThat(store.getBestUpdatesInRange(UInt64.ONE, 3)).isEmpty();
  }

  @TestTemplate
  public void getBestUpdatesInRange_shouldReturnEmptyListWhenCountIsZero() {
    store.addUpdate(createLightClientUpdateAtPeriod(1));

    assertThat(store.getBestUpdatesInRange(UInt64.ONE, 0)).isEmpty();
  }

  @TestTemplate
  public void getBestUpdatesInRange_shouldReturnPeriodsInOrderWithinCount() {
    final LightClientUpdate periodOne = createLightClientUpdateAtPeriod(1);
    final LightClientUpdate periodTwo = createLightClientUpdateAtPeriod(2);
    final LightClientUpdate periodThree = createLightClientUpdateAtPeriod(3);

    store.addUpdate(periodThree);
    store.addUpdate(periodOne);
    store.addUpdate(periodTwo);

    assertThat(store.getBestUpdatesInRange(UInt64.ONE, 2)).containsExactly(periodOne, periodTwo);
  }

  @TestTemplate
  public void getBestUpdatesInRange_shouldStopAtFirstMissingPeriod() {
    final LightClientUpdate periodOne = createLightClientUpdateAtPeriod(1);
    final LightClientUpdate periodThree = createLightClientUpdateAtPeriod(3);
    store.addUpdate(periodOne);
    store.addUpdate(periodThree);

    assertThat(store.getBestUpdatesInRange(UInt64.ONE, 500)).containsExactly(periodOne);
  }

  @TestTemplate
  public void getBestUpdatesInRange_shouldStartAtEarliestKnownPeriodInRange() {
    final LightClientUpdate periodTwo = createLightClientUpdateAtPeriod(2);
    final LightClientUpdate periodThree = createLightClientUpdateAtPeriod(3);
    store.addUpdate(periodTwo);
    store.addUpdate(periodThree);

    assertThat(store.getBestUpdatesInRange(UInt64.ONE, 3)).containsExactly(periodTwo, periodThree);
  }

  @TestTemplate
  public void addFinalityUpdate_shouldStoreFirstUpdate() {
    final LightClientFinalityUpdate update =
        dataStructureUtil.randomLightClientFinalityUpdate(periodOneStartSlot, periodOneStartSlot);

    store.addFinalityUpdate(update);

    assertThat(store.getLatestFinalityUpdate()).contains(update);
  }

  @TestTemplate
  public void addFinalityUpdate_shouldKeepHighestAttestedSlotEvenWhenFinalizedSlotIsLower() {
    final LightClientFinalityUpdate older =
        dataStructureUtil.randomLightClientFinalityUpdate(
            periodOneStartSlot, periodOneStartSlot.increment());
    final LightClientFinalityUpdate newer =
        dataStructureUtil.randomLightClientFinalityUpdate(
            periodOneStartSlot.increment(), periodOneStartSlot);

    store.addFinalityUpdate(older);
    store.addFinalityUpdate(newer);
    assertThat(store.getLatestFinalityUpdate()).contains(newer);

    store.addFinalityUpdate(older);
    assertThat(store.getLatestFinalityUpdate()).contains(newer);
  }

  @TestTemplate
  public void addFinalityUpdate_shouldBreakEqualAttestedSlotBySignatureSlot() {
    final LightClientFinalityUpdate older =
        dataStructureUtil.randomLightClientFinalityUpdate(
            periodOneStartSlot, periodOneStartSlot, periodOneStartSlot);
    final LightClientFinalityUpdate newer =
        dataStructureUtil.randomLightClientFinalityUpdate(
            periodOneStartSlot, periodOneStartSlot, periodOneStartSlot.increment());

    store.addFinalityUpdate(older);
    store.addFinalityUpdate(newer);
    assertThat(store.getLatestFinalityUpdate()).contains(newer);

    store.addFinalityUpdate(older);
    assertThat(store.getLatestFinalityUpdate()).contains(newer);
  }

  @TestTemplate
  public void addOptimisticUpdate_shouldStoreFirstUpdate() {
    final LightClientOptimisticUpdate update =
        dataStructureUtil.randomLightClientOptimisticUpdate(periodOneStartSlot);

    store.addOptimisticUpdate(update);

    assertThat(store.getLatestOptimisticUpdate()).contains(update);
  }

  @TestTemplate
  public void addOptimisticUpdate_shouldKeepHighestAttestedSlot() {
    final LightClientOptimisticUpdate older =
        dataStructureUtil.randomLightClientOptimisticUpdate(periodOneStartSlot);
    final LightClientOptimisticUpdate newer =
        dataStructureUtil.randomLightClientOptimisticUpdate(periodOneStartSlot.increment());

    store.addOptimisticUpdate(older);
    store.addOptimisticUpdate(newer);
    assertThat(store.getLatestOptimisticUpdate()).contains(newer);

    store.addOptimisticUpdate(older);
    assertThat(store.getLatestOptimisticUpdate()).contains(newer);
  }

  @TestTemplate
  public void addOptimisticUpdate_shouldBreakEqualAttestedSlotBySignatureSlot() {
    final LightClientOptimisticUpdate older =
        dataStructureUtil.randomLightClientOptimisticUpdate(periodOneStartSlot, periodOneStartSlot);
    final LightClientOptimisticUpdate newer =
        dataStructureUtil.randomLightClientOptimisticUpdate(
            periodOneStartSlot, periodOneStartSlot.increment());

    store.addOptimisticUpdate(older);
    store.addOptimisticUpdate(newer);
    assertThat(store.getLatestOptimisticUpdate()).contains(newer);

    store.addOptimisticUpdate(older);
    assertThat(store.getLatestOptimisticUpdate()).contains(newer);
  }

  private LightClientUpdate getBestUpdateAfterAdding(
      final LightClientUpdate first, final LightClientUpdate second) {
    final LightClientUpdateStore comparisonStore = new LightClientUpdateStore(spec);
    comparisonStore.addUpdate(first);
    comparisonStore.addUpdate(second);
    return comparisonStore.getBestUpdatesInRange(UInt64.ONE, 1).getFirst();
  }

  private LightClientUpdate createLightClientUpdateAtPeriod(final long period) {
    return dataStructureUtil
        .createRandomLightClientUpdateBuilder(periodOneStartSlot.times(period))
        .build();
  }

  private DataStructureUtil.RandomLightClientUpdateBuilder createLightClientUpdate() {
    return dataStructureUtil.createRandomLightClientUpdateBuilder(periodOneStartSlot);
  }
}
