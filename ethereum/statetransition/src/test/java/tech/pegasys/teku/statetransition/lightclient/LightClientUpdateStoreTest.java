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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBytes32Vector;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBytes32VectorSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientFinalityUpdate;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientFinalityUpdateSchema;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientHeader;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientHeaderSchema;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientOptimisticUpdate;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientOptimisticUpdateSchema;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientUpdate;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientUpdateSchema;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;
import tech.pegasys.teku.spec.util.DataStructureUtil;

/**
 * Fork parametrised because the sync committee and finality branch lengths change between
 * milestones, and {@code is_better_update} reads both branches through {@code isDefault()}.
 */
@TestSpecContext(allMilestones = true, ignoredMilestones = SpecMilestone.PHASE0)
public class LightClientUpdateStoreTest {

  /** Comparison tests keep both updates in this period so they collide on a single map key. */
  private static final UInt64 PERIOD = UInt64.ONE;

  private Spec spec;
  private DataStructureUtil dataStructureUtil;
  private LightClientUpdateStore store;

  private LightClientUpdateSchema updateSchema;
  private LightClientFinalityUpdateSchema finalityUpdateSchema;
  private LightClientOptimisticUpdateSchema optimisticUpdateSchema;
  private LightClientHeaderSchema<?> headerSchema;

  private long slotsPerPeriod;
  private long earlierSlot;
  private long laterSlot;

  /** Smallest participant count satisfying {@code participants * 3 >= size * 2}. */
  private int supermajority;

  private int fullParticipation;

  @BeforeEach
  void setUp(final SpecContext specContext) {
    spec = specContext.getSpec();
    dataStructureUtil = specContext.getDataStructureUtil();
    store = new LightClientUpdateStore(spec);

    final SchemaDefinitionsAltair schemaDefinitions =
        SchemaDefinitionsAltair.required(spec.getGenesisSchemaDefinitions());
    updateSchema = schemaDefinitions.getLightClientUpdateSchema();
    finalityUpdateSchema = schemaDefinitions.getLightClientFinalityUpdateSchema();
    optimisticUpdateSchema = schemaDefinitions.getLightClientOptimisticUpdateSchema();
    headerSchema = schemaDefinitions.getLightClientHeaderSchema();

    final SpecConfigAltair config = SpecConfigAltair.required(spec.getGenesisSpecConfig());
    slotsPerPeriod = (long) config.getEpochsPerSyncCommitteePeriod() * config.getSlotsPerEpoch();
    earlierSlot = slotsPerPeriod;
    laterSlot = slotsPerPeriod + 1;

    fullParticipation = config.getSyncCommitteeSize();
    supermajority = (fullParticipation * 2 + 2) / 3;
  }

  // is_better_update: one test per spec clause, plus a pair showing the earlier clause takes
  // priority. All go through the store, so each also covers merge()'s argument order.

  @TestTemplate
  public void isBetterUpdate_shouldPreferSupermajority() {
    assertPrefers(
        update().participants(supermajority).build(),
        update().participants(supermajority - 1).build());
  }

  @TestTemplate
  public void isBetterUpdate_shouldPreferMoreParticipantsWhenNeitherHasSupermajority() {
    assertPrefers(
        update().participants(supermajority - 1).build(),
        update().participants(supermajority - 2).build());
  }

  @TestTemplate
  public void isBetterUpdate_shouldNotCompareParticipationEarlyWhenBothHaveSupermajority() {
    // The participation clause is guarded by `!newHasSupermajority`. Without that guard the
    // lower-participation update can never win and the sync committee clause below is skipped.
    assertPrefers(
        update().participants(supermajority).syncCommitteeBranch(true).build(),
        update().participants(fullParticipation).syncCommitteeBranch(false).build());
  }

  @TestTemplate
  public void isBetterUpdate_shouldPreferRelevantSyncCommittee() {
    assertPrefers(
        update().syncCommitteeBranch(true).build(), update().syncCommitteeBranch(false).build());
  }

  @TestTemplate
  public void isBetterUpdate_shouldIgnoreSyncCommitteeBranchFromAnotherPeriod() {
    // A non-empty branch is only relevant when the attested and signature periods match.
    assertPrefers(
        update().participants(supermajority).syncCommitteeBranch(true).build(),
        update()
            .participants(fullParticipation)
            .syncCommitteeBranch(true)
            .signatureSlot(slotsPerPeriod * 3)
            .build());
  }

  @TestTemplate
  public void isBetterUpdate_shouldPreferSupermajorityOverRelevantSyncCommittee() {
    assertPrefers(
        update().participants(supermajority).syncCommitteeBranch(false).build(),
        update().participants(supermajority - 1).syncCommitteeBranch(true).build());
  }

  @TestTemplate
  public void isBetterUpdate_shouldPreferFinality() {
    assertPrefers(update().finalityBranch(true).build(), update().finalityBranch(false).build());
  }

  @TestTemplate
  public void isBetterUpdate_shouldPreferRelevantSyncCommitteeOverFinality() {
    assertPrefers(
        update().syncCommitteeBranch(true).finalityBranch(false).build(),
        update().syncCommitteeBranch(false).finalityBranch(true).build());
  }

  @TestTemplate
  public void isBetterUpdate_shouldPreferSyncCommitteeFinality() {
    assertPrefers(
        update().finalityBranch(true).finalizedSlot(laterSlot).build(),
        update().finalityBranch(true).finalizedSlot(0).build());
  }

  @TestTemplate
  public void isBetterUpdate_shouldSkipSyncCommitteeFinalityWhenNeitherHasFinality() {
    // The clause is guarded by `newHasFinality`, so participation must decide instead.
    assertPrefers(
        update().finalityBranch(false).finalizedSlot(0).participants(fullParticipation).build(),
        update()
            .finalityBranch(false)
            .finalizedSlot(laterSlot)
            .participants(supermajority)
            .build());
  }

  @TestTemplate
  public void isBetterUpdate_shouldPreferFinalityOverParticipation() {
    assertPrefers(
        update().finalityBranch(true).participants(supermajority).build(),
        update().finalityBranch(false).participants(fullParticipation).build());
  }

  @TestTemplate
  public void isBetterUpdate_shouldPreferMoreParticipantsAsTiebreak() {
    assertPrefers(
        update().participants(fullParticipation).build(),
        update().participants(supermajority).build());
  }

  @TestTemplate
  public void isBetterUpdate_shouldPreferOlderAttestedSlot() {
    // The last two clauses deliberately invert: the spec prefers older data.
    assertPrefers(
        update().attestedSlot(earlierSlot).build(), update().attestedSlot(laterSlot).build());
  }

  @TestTemplate
  public void isBetterUpdate_shouldPreferMoreParticipantsOverOlderAttestedSlot() {
    assertPrefers(
        update().participants(fullParticipation).attestedSlot(laterSlot).build(),
        update().participants(supermajority).attestedSlot(earlierSlot).build());
  }

  @TestTemplate
  public void isBetterUpdate_shouldPreferOlderSignatureSlot() {
    assertPrefers(
        update().signatureSlot(earlierSlot).build(), update().signatureSlot(laterSlot).build());
  }

  @TestTemplate
  public void isBetterUpdate_shouldKeepExistingUpdateWhenIdentical() {
    // Every clause falls through to `signatureSlot < signatureSlot`, which must be false.
    final LightClientUpdate update = update().build();

    store.syncLightClientUpdate(update);
    store.syncLightClientUpdate(update);

    assertThat(store.getBestUpdates(PERIOD, PERIOD.increment())).containsExactly(update);
  }

  @TestTemplate
  public void syncLightClientUpdate_shouldKeepUpdatesForDifferentPeriodsSeparately() {
    final LightClientUpdate periodOne = updateAtPeriod(1);
    final LightClientUpdate periodTwo = updateAtPeriod(2);

    store.syncLightClientUpdate(periodOne);
    store.syncLightClientUpdate(periodTwo);

    assertThat(store.getBestUpdates(UInt64.ONE, UInt64.valueOf(3)))
        .containsExactly(periodOne, periodTwo);
  }

  @TestTemplate
  public void syncLightClientUpdate_shouldKeyByAttestedPeriodNotSignaturePeriod() {
    final LightClientUpdate update =
        update().attestedSlot(earlierSlot).signatureSlot(slotsPerPeriod * 2).build();

    store.syncLightClientUpdate(update);

    assertThat(store.getBestUpdates(UInt64.ONE, UInt64.valueOf(2))).containsExactly(update);
    assertThat(store.getBestUpdates(UInt64.valueOf(2), UInt64.valueOf(3))).isEmpty();
  }

  @TestTemplate
  public void getBestUpdates_shouldReturnEmptyListWhenStoreIsEmpty() {
    assertThat(store.getBestUpdates(UInt64.ZERO, UInt64.valueOf(10))).isEmpty();
  }

  @TestTemplate
  public void getBestUpdates_shouldReturnEmptyListWhenRangeIsEmpty() {
    store.syncLightClientUpdate(updateAtPeriod(1));

    assertThat(store.getBestUpdates(PERIOD, PERIOD)).isEmpty();
  }

  @TestTemplate
  public void getBestUpdates_shouldExcludeEndPeriod() {
    final LightClientUpdate periodOne = updateAtPeriod(1);
    final LightClientUpdate periodTwo = updateAtPeriod(2);
    final LightClientUpdate periodThree = updateAtPeriod(3);
    Stream.of(periodOne, periodTwo, periodThree).forEach(store::syncLightClientUpdate);

    assertThat(store.getBestUpdates(UInt64.ONE, UInt64.valueOf(3)))
        .containsExactly(periodOne, periodTwo);
  }

  @TestTemplate
  public void getBestUpdates_shouldSkipMissingPeriods() {
    final LightClientUpdate periodOne = updateAtPeriod(1);
    final LightClientUpdate periodThree = updateAtPeriod(3);
    store.syncLightClientUpdate(periodOne);
    store.syncLightClientUpdate(periodThree);

    assertThat(store.getBestUpdates(UInt64.ONE, UInt64.valueOf(5)))
        .containsExactly(periodOne, periodThree);
  }

  @TestTemplate
  public void getBestUpdates_shouldReturnAvailableUpdatesWhenRangeExtendsPastEnd() {
    final LightClientUpdate periodOne = updateAtPeriod(1);
    store.syncLightClientUpdate(periodOne);

    assertThat(store.getBestUpdates(UInt64.ONE, UInt64.valueOf(500))).containsExactly(periodOne);
  }

  @TestTemplate
  public void getBestUpdates_shouldReturnUpdatesInAscendingPeriodOrder() {
    final LightClientUpdate periodOne = updateAtPeriod(1);
    final LightClientUpdate periodTwo = updateAtPeriod(2);
    final LightClientUpdate periodThree = updateAtPeriod(3);

    store.syncLightClientUpdate(periodThree);
    store.syncLightClientUpdate(periodOne);
    store.syncLightClientUpdate(periodTwo);

    assertThat(store.getBestUpdates(UInt64.ONE, UInt64.valueOf(4)))
        .containsExactly(periodOne, periodTwo, periodThree);
  }

  @TestTemplate
  public void getBestUpdates_shouldReturnImmutableList() {
    final LightClientUpdate update = updateAtPeriod(1);
    store.syncLightClientUpdate(update);

    final List<LightClientUpdate> updates = store.getBestUpdates(UInt64.ONE, UInt64.valueOf(2));

    assertThatThrownBy(() -> updates.add(update)).isInstanceOf(UnsupportedOperationException.class);
  }

  // Latest finality and optimistic updates prefer newer data, the opposite of is_better_update.

  @TestTemplate
  public void syncLightClientFinalityUpdate_shouldStoreFirstUpdate() {
    final LightClientFinalityUpdate update = finalityUpdate(earlierSlot, earlierSlot);

    store.syncLightClientFinalityUpdate(update);

    assertThat(store.getLatestFinalityUpdate()).contains(update);
  }

  @TestTemplate
  public void syncLightClientFinalityUpdate_shouldReplaceOnHigherFinalizedSlot() {
    final LightClientFinalityUpdate older = finalityUpdate(laterSlot, earlierSlot);
    final LightClientFinalityUpdate newer = finalityUpdate(laterSlot, laterSlot);

    store.syncLightClientFinalityUpdate(older);
    store.syncLightClientFinalityUpdate(newer);

    assertThat(store.getLatestFinalityUpdate()).contains(newer);
  }

  @TestTemplate
  public void syncLightClientFinalityUpdate_shouldNotReplaceOnLowerFinalizedSlot() {
    final LightClientFinalityUpdate newer = finalityUpdate(laterSlot, laterSlot);
    final LightClientFinalityUpdate older = finalityUpdate(laterSlot, earlierSlot);

    store.syncLightClientFinalityUpdate(newer);
    store.syncLightClientFinalityUpdate(older);

    assertThat(store.getLatestFinalityUpdate()).contains(newer);
  }

  @TestTemplate
  public void
      syncLightClientFinalityUpdate_shouldReplaceOnHigherAttestedSlotWhenFinalizedIsEqual() {
    final LightClientFinalityUpdate older = finalityUpdate(earlierSlot, earlierSlot);
    final LightClientFinalityUpdate newer = finalityUpdate(laterSlot, earlierSlot);

    store.syncLightClientFinalityUpdate(older);
    store.syncLightClientFinalityUpdate(newer);

    assertThat(store.getLatestFinalityUpdate()).contains(newer);
  }

  @TestTemplate
  public void
      syncLightClientFinalityUpdate_shouldNotReplaceOnLowerAttestedSlotWhenFinalizedIsEqual() {
    final LightClientFinalityUpdate newer = finalityUpdate(laterSlot, earlierSlot);
    final LightClientFinalityUpdate older = finalityUpdate(earlierSlot, earlierSlot);

    store.syncLightClientFinalityUpdate(newer);
    store.syncLightClientFinalityUpdate(older);

    assertThat(store.getLatestFinalityUpdate()).contains(newer);
  }

  @TestTemplate
  public void syncLightClientOptimisticUpdate_shouldStoreFirstUpdate() {
    final LightClientOptimisticUpdate update = optimisticUpdate(earlierSlot);

    store.syncLightClientOptimisticUpdate(update);

    assertThat(store.getLatestOptimisticUpdate()).contains(update);
  }

  @TestTemplate
  public void syncLightClientOptimisticUpdate_shouldReplaceOnHigherAttestedSlot() {
    final LightClientOptimisticUpdate older = optimisticUpdate(earlierSlot);
    final LightClientOptimisticUpdate newer = optimisticUpdate(laterSlot);

    store.syncLightClientOptimisticUpdate(older);
    store.syncLightClientOptimisticUpdate(newer);

    assertThat(store.getLatestOptimisticUpdate()).contains(newer);
  }

  @TestTemplate
  public void syncLightClientOptimisticUpdate_shouldNotReplaceOnLowerAttestedSlot() {
    final LightClientOptimisticUpdate newer = optimisticUpdate(laterSlot);
    final LightClientOptimisticUpdate older = optimisticUpdate(earlierSlot);

    store.syncLightClientOptimisticUpdate(newer);
    store.syncLightClientOptimisticUpdate(older);

    assertThat(store.getLatestOptimisticUpdate()).contains(newer);
  }

  /**
   * Asserts {@code preferred} wins over {@code other} whichever is inserted first. Order
   * independence is what catches a flipped {@code merge()} remapping function, which is otherwise
   * silent.
   */
  private void assertPrefers(final LightClientUpdate preferred, final LightClientUpdate other) {
    final LightClientUpdateStore otherFirst = new LightClientUpdateStore(spec);
    otherFirst.syncLightClientUpdate(other);
    otherFirst.syncLightClientUpdate(preferred);
    assertThat(otherFirst.getBestUpdates(PERIOD, PERIOD.increment()))
        .as("preferred update inserted second")
        .containsExactly(preferred);

    final LightClientUpdateStore preferredFirst = new LightClientUpdateStore(spec);
    preferredFirst.syncLightClientUpdate(preferred);
    preferredFirst.syncLightClientUpdate(other);
    assertThat(preferredFirst.getBestUpdates(PERIOD, PERIOD.increment()))
        .as("preferred update inserted first")
        .containsExactly(preferred);
  }

  private LightClientUpdate updateAtPeriod(final long period) {
    return update().attestedSlot(period * slotsPerPeriod).build();
  }

  private UpdateBuilder update() {
    return new UpdateBuilder();
  }

  /**
   * Defaults put both headers in {@link #PERIOD} with a supermajority and both branches present.
   */
  private final class UpdateBuilder {
    private long attestedSlot = earlierSlot;
    private long signatureSlot = earlierSlot;
    private long finalizedSlot = earlierSlot;
    private int participants = supermajority;
    private boolean syncCommitteeBranch = true;
    private boolean finalityBranch = true;

    UpdateBuilder attestedSlot(final long slot) {
      this.attestedSlot = slot;
      return this;
    }

    UpdateBuilder signatureSlot(final long slot) {
      this.signatureSlot = slot;
      return this;
    }

    UpdateBuilder finalizedSlot(final long slot) {
      this.finalizedSlot = slot;
      return this;
    }

    UpdateBuilder participants(final int participants) {
      this.participants = participants;
      return this;
    }

    UpdateBuilder syncCommitteeBranch(final boolean present) {
      this.syncCommitteeBranch = present;
      return this;
    }

    UpdateBuilder finalityBranch(final boolean present) {
      this.finalityBranch = present;
      return this;
    }

    LightClientUpdate build() {
      return updateSchema.create(
          header(attestedSlot),
          updateSchema.getNextSyncCommitteeSchema().getDefault(),
          branch(updateSchema.getSyncCommitteeBranchSchema(), syncCommitteeBranch),
          header(finalizedSlot),
          branch(updateSchema.getFinalityBranchSchema(), finalityBranch),
          syncAggregate(participants),
          SszUInt64.of(UInt64.valueOf(signatureSlot)));
    }
  }

  private LightClientFinalityUpdate finalityUpdate(
      final long attestedSlot, final long finalizedSlot) {
    return finalityUpdateSchema.create(
        header(attestedSlot),
        header(finalizedSlot),
        branch(finalityUpdateSchema.getFinalizedBranchSchema(), true),
        syncAggregate(supermajority),
        SszUInt64.of(UInt64.valueOf(attestedSlot)));
  }

  private LightClientOptimisticUpdate optimisticUpdate(final long attestedSlot) {
    return optimisticUpdateSchema.create(
        header(attestedSlot),
        syncAggregate(supermajority),
        SszUInt64.of(UInt64.valueOf(attestedSlot)));
  }

  private SyncAggregate syncAggregate(final int participants) {
    return dataStructureUtil.randomSyncAggregate(IntStream.range(0, participants).toArray());
  }

  private LightClientHeader header(final long slot) {
    return headerSchema.create(
        new BeaconBlockHeader(
            UInt64.valueOf(slot), UInt64.ZERO, Bytes32.ZERO, Bytes32.ZERO, Bytes32.ZERO));
  }

  /**
   * {@code DataStructureUtil.randomSszBytes32Vector} only randomises {@code length / 10} elements,
   * which is zero for these branches — it would return the default and read as absent.
   */
  private SszBytes32Vector branch(
      final SszBytes32VectorSchema<SszBytes32Vector> schema, final boolean present) {
    if (!present) {
      return schema.getDefault();
    }
    return Stream.generate(dataStructureUtil::randomBytes32)
        .limit(schema.getLength())
        .collect(schema.collectorUnboxed());
  }
}
