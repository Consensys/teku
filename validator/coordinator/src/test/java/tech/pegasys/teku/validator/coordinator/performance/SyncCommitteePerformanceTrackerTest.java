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

package tech.pegasys.teku.validator.coordinator.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.BeaconBlockBodyAltair;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.BeaconBlockBodySchemaAltair;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeSignature;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeSignatureSchema;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

class SyncCommitteePerformanceTrackerTest {

  private final Spec spec = TestSpecFactory.createMinimalAltair();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final CombinedChainDataClient combinedChainDataClient =
      mock(CombinedChainDataClient.class);

  private final SchemaDefinitionsAltair schemaDefinitionsAltair =
      SchemaDefinitionsAltair.required(spec.getGenesisSchemaDefinitions());
  private final SyncCommitteeSignatureSchema signatureSchema =
      schemaDefinitionsAltair.getSyncCommitteeSignatureSchema();

  private SyncCommitteePerformanceTracker tracker =
      new SyncCommitteePerformanceTracker(spec, combinedChainDataClient);

  @BeforeEach
  void setUp() {
    when(combinedChainDataClient.getBlockAtSlotExact(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
  }

  @Test
  void shouldCalculatePerformanceWhenNoSyncCommitteeDutiesExpectedInEpoch() {
    assertThat(tracker.calculatePerformance(UInt64.ONE))
        .isCompletedWithValue(new SyncCommitteePerformance(0, 0, 0, 0));
    verifyNoInteractions(combinedChainDataClient);
  }

  @Test
  void shouldExpectSignaturesForEachPositionInCommitteeAtEachSlot() {
    tracker.saveExpectedSyncCommitteeParticipant(1, Set.of(2, 5, 6), UInt64.valueOf(3));

    final int expected = 3 * spec.getSlotsPerEpoch(UInt64.ZERO);
    assertThat(calculatePerformance(UInt64.ONE).getNumberOfExpectedSignatures())
        .isEqualTo(expected);
  }

  @Test
  void shouldCountNumberOfProducedSignatures() {
    tracker.saveExpectedSyncCommitteeParticipant(1, Set.of(2, 7, 8), UInt64.valueOf(3));
    tracker.saveProducedSyncCommitteeSignature(createSignature(1, 1));
    tracker.saveProducedSyncCommitteeSignature(createSignature(1, 3));

    assertThat(calculatePerformance(UInt64.ZERO).getNumberOfProducedSignatures()).isEqualTo(6);
  }

  @Test
  void shouldCountNumberOfCorrectSignatures() {
    final Spec specSpy = spy(spec);
    tracker = new SyncCommitteePerformanceTracker(specSpy, combinedChainDataClient);
    final Bytes32 slot1Hash = dataStructureUtil.randomBytes32();
    final Bytes32 wrongBlockRoot = dataStructureUtil.randomBytes32();
    final SignedBlockAndState chainHead = dataStructureUtil.randomSignedBlockAndState(3);
    when(combinedChainDataClient.getChainHead())
        .thenReturn(Optional.of(StateAndBlockSummary.create(chainHead)));
    doReturn(dataStructureUtil.randomBytes32())
        .when(specSpy)
        .getBlockRootAtSlot(eq(chainHead.getState()), any());
    doReturn(slot1Hash).when(specSpy).getBlockRootAtSlot(chainHead.getState(), UInt64.ONE);

    tracker.saveExpectedSyncCommitteeParticipant(1, Set.of(2, 7, 8), UInt64.valueOf(3));
    tracker.saveProducedSyncCommitteeSignature(createSignature(1, 1, slot1Hash));
    tracker.saveProducedSyncCommitteeSignature(createSignature(1, 2, wrongBlockRoot));
    tracker.saveProducedSyncCommitteeSignature(createSignature(1, 3, wrongBlockRoot));
    tracker.saveProducedSyncCommitteeSignature(createSignature(1, 4, chainHead.getRoot()));

    // Slots 1 and 4 are correct, 2 and 3 are incorrect.
    assertThat(calculatePerformance(UInt64.ZERO).getNumberOfCorrectSignatures()).isEqualTo(6);
  }

  @Test
  void shouldCountNumberOfIncludedSignatures() {
    tracker.saveExpectedSyncCommitteeParticipant(1, Set.of(2, 7, 8), UInt64.valueOf(3));

    // Not included as no block was present
    tracker.saveProducedSyncCommitteeSignature(createSignature(1, 1));

    // Block produced, but this signature not included
    tracker.saveProducedSyncCommitteeSignature(createSignature(1, 2));
    withSyncAggregate(3, 9, 12, 15);

    // Included (along with some other signatures)
    tracker.saveProducedSyncCommitteeSignature(createSignature(1, 3));
    withSyncAggregate(4, 2, 7, 8, 9, 12, 15);

    assertThat(calculatePerformance(UInt64.ZERO).getNumberOfIncludedSignatures()).isEqualTo(3);
  }

  private SyncCommitteeSignature createSignature(final int validatorIndex, final int slot) {
    return createSignature(validatorIndex, slot, Bytes32.ZERO);
  }

  private SyncCommitteeSignature createSignature(
      final int validatorIndex, final int slot, final Bytes32 blockRoot) {
    return signatureSchema.create(
        UInt64.valueOf(slot), blockRoot, UInt64.valueOf(validatorIndex), BLSSignature.empty());
  }

  private SyncCommitteePerformance calculatePerformance(final UInt64 epoch) {
    final SafeFuture<SyncCommitteePerformance> result = tracker.calculatePerformance(epoch);
    assertThat(result).isCompleted();
    return result.join();
  }

  private void withSyncAggregate(final int slot, final Integer... includedCommitteeIndices) {
    final SignedBeaconBlock signedBlock = mock(SignedBeaconBlock.class);
    final BeaconBlock block = mock(BeaconBlock.class);
    final BeaconBlockBodyAltair blockBody = mock(BeaconBlockBodyAltair.class);
    final SyncAggregate syncAggregate =
        ((BeaconBlockBodySchemaAltair) schemaDefinitionsAltair.getBeaconBlockBodySchema())
            .getSyncAggregateSchema()
            .create(List.of(includedCommitteeIndices), BLSSignature.empty());
    when(signedBlock.getMessage()).thenReturn(block);
    when(block.getBody()).thenReturn(blockBody);
    when(blockBody.toVersionAltair()).thenReturn(Optional.of(blockBody));
    when(blockBody.getSyncAggregate()).thenReturn(syncAggregate);
    when(combinedChainDataClient.getBlockAtSlotExact(UInt64.valueOf(slot)))
        .thenReturn(SafeFuture.completedFuture(Optional.of(signedBlock)));
  }
}
