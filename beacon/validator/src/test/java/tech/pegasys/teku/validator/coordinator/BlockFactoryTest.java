/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.validator.coordinator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregateAssert.assertThatSyncAggregate;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.type.Bytes8;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.BeaconBlockBodyAltair;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.BeaconBlockBodySchemaAltair;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.merge.BeaconBlockBodyMerge;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.util.BeaconBlockBodyLists;
import tech.pegasys.teku.spec.executionengine.ExecutionEngineChannel;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.EpochProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.SlotProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.StateTransitionException;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsMerge;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.BeaconChainUtil;
import tech.pegasys.teku.statetransition.OperationPool;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifier;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeContributionPool;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;

@SuppressWarnings("unchecked")
class BlockFactoryTest {
  private static final Eth1Data ETH1_DATA = new Eth1Data();

  final AggregatingAttestationPool attestationsPool = mock(AggregatingAttestationPool.class);
  final OperationPool<AttesterSlashing> attesterSlashingPool = mock(OperationPool.class);
  final OperationPool<ProposerSlashing> proposerSlashingPool = mock(OperationPool.class);
  final OperationPool<SignedVoluntaryExit> voluntaryExitPool = mock(OperationPool.class);
  final ForkChoiceNotifier forkChoiceNotifier = mock(ForkChoiceNotifier.class);
  final ExecutionEngineChannel executionEngine = mock(ExecutionEngineChannel.class);
  final SyncCommitteeContributionPool syncCommitteeContributionPool =
      mock(SyncCommitteeContributionPool.class);
  final DepositProvider depositProvider = mock(DepositProvider.class);
  final Eth1DataCache eth1DataCache = mock(Eth1DataCache.class);
  ExecutionPayload executionPayload;

  @Test
  public void shouldCreateBlockAfterNormalSlot() throws Exception {
    assertBlockCreated(1, TestSpecFactory.createMinimalPhase0());
  }

  @Test
  public void shouldCreateBlockAfterSkippedSlot() throws Exception {
    assertBlockCreated(2, TestSpecFactory.createMinimalPhase0());
  }

  @Test
  public void shouldCreateBlockAfterMultipleSkippedSlot() throws Exception {
    assertBlockCreated(5, TestSpecFactory.createMinimalPhase0());
  }

  @Test
  void shouldIncludeSyncAggregateWhenAltairIsActive() throws Exception {
    final BeaconBlock block = assertBlockCreated(1, TestSpecFactory.createMinimalAltair());
    final SyncAggregate result = getSyncAggregate(block);
    assertThatSyncAggregate(result).isNotNull();
    verify(syncCommitteeContributionPool)
        .createSyncAggregateForBlock(UInt64.ONE, block.getParentRoot());
  }

  @Test
  void shouldIncludeExecutionPayloadWhenMergeIsActive() throws Exception {
    final BeaconBlock block = assertBlockCreated(1, TestSpecFactory.createMinimalMerge());
    final ExecutionPayload result = getExecutionPayload(block);
    assertThat(result).isEqualTo(executionPayload);
  }

  private SyncAggregate getSyncAggregate(final BeaconBlock block) {
    return BeaconBlockBodyAltair.required(block.getBody()).getSyncAggregate();
  }

  private ExecutionPayload getExecutionPayload(final BeaconBlock block) {
    return BeaconBlockBodyMerge.required(block.getBody()).getExecutionPayload();
  }

  private BeaconBlock assertBlockCreated(final int blockSlot, final Spec spec)
      throws EpochProcessingException, SlotProcessingException, StateTransitionException {
    final UInt64 newSlot = UInt64.valueOf(blockSlot);
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    final BeaconBlockBodyLists blockBodyLists = BeaconBlockBodyLists.ofSpec(spec);
    final RecentChainData recentChainData = MemoryOnlyRecentChainData.create(spec);
    final BeaconChainUtil beaconChainUtil = BeaconChainUtil.create(spec, 1, recentChainData);
    final SszList<Deposit> deposits = blockBodyLists.createDeposits();
    final SszList<Attestation> attestations = blockBodyLists.createAttestations();
    final SszList<AttesterSlashing> attesterSlashings = blockBodyLists.createAttesterSlashings();
    final SszList<ProposerSlashing> proposerSlashings = blockBodyLists.createProposerSlashings();
    final SszList<SignedVoluntaryExit> voluntaryExits = blockBodyLists.createVoluntaryExits();

    if (spec.getGenesisSpec().getMilestone().isGreaterThanOrEqualTo(SpecMilestone.MERGE)) {
      executionPayload =
          SchemaDefinitionsMerge.required(spec.getGenesisSpec().getSchemaDefinitions())
              .getExecutionPayloadSchema()
              .getDefault();
    } else {
      executionPayload = null;
    }

    final Bytes32 graffiti = dataStructureUtil.randomBytes32();
    final BlockFactory blockFactory =
        new BlockFactory(
            spec,
            new BlockOperationSelectorFactory(
                spec,
                attestationsPool,
                attesterSlashingPool,
                proposerSlashingPool,
                voluntaryExitPool,
                syncCommitteeContributionPool,
                depositProvider,
                eth1DataCache,
                graffiti,
                forkChoiceNotifier,
                executionEngine));

    when(depositProvider.getDeposits(any(), any())).thenReturn(deposits);
    when(attestationsPool.getAttestationsForBlock(any(), any(), any())).thenReturn(attestations);
    when(attesterSlashingPool.getItemsForBlock(any(), any(), any())).thenReturn(attesterSlashings);
    when(proposerSlashingPool.getItemsForBlock(any(), any(), any())).thenReturn(proposerSlashings);
    when(voluntaryExitPool.getItemsForBlock(any(), any(), any())).thenReturn(voluntaryExits);
    when(eth1DataCache.getEth1Vote(any())).thenReturn(ETH1_DATA);
    when(forkChoiceNotifier.getPayloadId(any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(Bytes8.fromHexStringLenient("0x0"))));
    when(executionEngine.getPayload(any(), any()))
        .thenReturn(SafeFuture.completedFuture(executionPayload));
    beaconChainUtil.initializeStorage();

    final BLSSignature randaoReveal = dataStructureUtil.randomSignature();
    final StateAndBlockSummary bestBlockAndState = recentChainData.getChainHead().orElseThrow();
    final Bytes32 bestBlockRoot = bestBlockAndState.getRoot();
    final BeaconState blockSlotState =
        recentChainData
            .retrieveStateAtSlot(new SlotAndBlockRoot(UInt64.valueOf(blockSlot), bestBlockRoot))
            .join()
            .orElseThrow();

    when(syncCommitteeContributionPool.createSyncAggregateForBlock(newSlot, bestBlockRoot))
        .thenAnswer(invocation -> createEmptySyncAggregate(spec));

    final BeaconBlock block =
        blockFactory.createUnsignedBlock(blockSlotState, newSlot, randaoReveal, Optional.empty());

    assertThat(block).isNotNull();
    assertThat(block.getSlot()).isEqualTo(newSlot);
    assertThat(block.getBody().getRandaoReveal()).isEqualTo(randaoReveal);
    assertThat(block.getBody().getEth1Data()).isEqualTo(ETH1_DATA);
    assertThat(block.getBody().getDeposits()).isEqualTo(deposits);
    assertThat(block.getBody().getAttestations()).isEqualTo(attestations);
    assertThat(block.getBody().getAttesterSlashings()).isEqualTo(attesterSlashings);
    assertThat(block.getBody().getProposerSlashings()).isEqualTo(proposerSlashings);
    assertThat(block.getBody().getVoluntaryExits()).isEqualTo(voluntaryExits);
    assertThat(block.getBody().getGraffiti()).isEqualTo(graffiti);
    return block;
  }

  private SyncAggregate createEmptySyncAggregate(final Spec spec) {
    return BeaconBlockBodySchemaAltair.required(
            spec.getGenesisSchemaDefinitions().getBeaconBlockBodySchema())
        .getSyncAggregateSchema()
        .createEmpty();
  }
}
