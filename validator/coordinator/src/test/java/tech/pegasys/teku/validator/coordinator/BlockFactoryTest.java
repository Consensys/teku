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

import static com.google.common.primitives.UnsignedLong.ONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.datastructures.blocks.BeaconBlockBodyLists.createAttestations;
import static tech.pegasys.teku.datastructures.blocks.BeaconBlockBodyLists.createAttesterSlashings;
import static tech.pegasys.teku.datastructures.blocks.BeaconBlockBodyLists.createDeposits;
import static tech.pegasys.teku.datastructures.blocks.BeaconBlockBodyLists.createProposerSlashings;
import static tech.pegasys.teku.datastructures.blocks.BeaconBlockBodyLists.createVoluntaryExits;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.core.BlockProposalUtil;
import tech.pegasys.teku.core.StateTransition;
import tech.pegasys.teku.core.StateTransitionException;
import tech.pegasys.teku.core.exceptions.EpochProcessingException;
import tech.pegasys.teku.core.exceptions.SlotProcessingException;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.datastructures.operations.Deposit;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.ssz.SSZTypes.SSZMutableList;
import tech.pegasys.teku.statetransition.BeaconChainUtil;
import tech.pegasys.teku.statetransition.OperationPool;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;

@SuppressWarnings("unchecked")
class BlockFactoryTest {

  public static final Eth1Data ETH1_DATA = new Eth1Data();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final RecentChainData recentChainData = MemoryOnlyRecentChainData.create(new EventBus());
  private final BeaconChainUtil beaconChainUtil = BeaconChainUtil.create(1, recentChainData);
  private final AggregatingAttestationPool attestationsPool =
      mock(AggregatingAttestationPool.class);
  private final OperationPool<AttesterSlashing> attesterSlashingPool = mock(OperationPool.class);
  private final OperationPool<ProposerSlashing> proposerSlashingPool = mock(OperationPool.class);
  private final OperationPool<SignedVoluntaryExit> voluntaryExitPool = mock(OperationPool.class);
  private final DepositProvider depositProvider = mock(DepositProvider.class);
  private final Eth1DataCache eth1DataCache = mock(Eth1DataCache.class);
  private final StateTransition stateTransition = new StateTransition();
  private final SSZMutableList<Deposit> deposits = createDeposits();
  private final SSZMutableList<Attestation> attestations = createAttestations();
  private final SSZMutableList<AttesterSlashing> attesterSlashings = createAttesterSlashings();
  private final SSZMutableList<ProposerSlashing> proposerSlashings = createProposerSlashings();
  private final SSZMutableList<SignedVoluntaryExit> voluntaryExits = createVoluntaryExits();

  private final Bytes32 graffiti = dataStructureUtil.randomBytes32();
  private final BlockFactory blockFactory =
      new BlockFactory(
          new BlockProposalUtil(stateTransition),
          stateTransition,
          attestationsPool,
          attesterSlashingPool,
          proposerSlashingPool,
          voluntaryExitPool,
          depositProvider,
          eth1DataCache,
          graffiti);

  @BeforeEach
  void setUp() {
    when(depositProvider.getDeposits(any(), any())).thenReturn(deposits);
    when(attestationsPool.getAttestationsForBlock(any())).thenReturn(attestations);
    when(attesterSlashingPool.getItemsForBlock(any())).thenReturn(attesterSlashings);
    when(proposerSlashingPool.getItemsForBlock(any())).thenReturn(proposerSlashings);
    when(voluntaryExitPool.getItemsForBlock(any())).thenReturn(voluntaryExits);
    when(eth1DataCache.getEth1Vote(any())).thenReturn(ETH1_DATA);
    beaconChainUtil.initializeStorage();
  }

  @Test
  public void shouldCreateBlockAfterNormalSlot() throws Exception {
    final UnsignedLong newSlot = recentChainData.getBestSlot().plus(ONE);
    assertBlockCreated(newSlot);
  }

  @Test
  public void shouldCreateBlockAfterSkippedSlot() throws Exception {
    final UnsignedLong newSlot = recentChainData.getBestSlot().plus(UnsignedLong.valueOf(2));
    assertBlockCreated(newSlot);
  }

  @Test
  public void shouldCreateBlockAfterMultipleSkippedSlot() throws Exception {
    final UnsignedLong newSlot = recentChainData.getBestSlot().plus(UnsignedLong.valueOf(5));
    assertBlockCreated(newSlot);
  }

  private void assertBlockCreated(final UnsignedLong newSlot)
      throws EpochProcessingException, SlotProcessingException, StateTransitionException {
    final BLSSignature randaoReveal = dataStructureUtil.randomSignature();
    final BeaconBlockAndState bestBlockAndState =
        recentChainData.getBestBlockAndState().orElseThrow();
    final Bytes32 bestBlockRoot = bestBlockAndState.getRoot();
    final BeaconBlock previousBlock = bestBlockAndState.getBlock();
    final BeaconState previousState =
        recentChainData.retrieveBlockState(bestBlockRoot).join().orElseThrow();
    final BeaconBlock block =
        blockFactory.createUnsignedBlock(
            previousState, previousBlock, newSlot, randaoReveal, Optional.empty());

    assertThat(block).isNotNull();
    assertThat(block.getSlot()).isEqualTo(newSlot);
    assertThat(block.getBody().getRandao_reveal()).isEqualTo(randaoReveal);
    assertThat(block.getBody().getEth1_data()).isEqualTo(ETH1_DATA);
    assertThat(block.getBody().getDeposits()).isEqualTo(deposits);
    assertThat(block.getBody().getAttestations()).isEqualTo(attestations);
    assertThat(block.getBody().getAttester_slashings()).isEqualTo(attesterSlashings);
    assertThat(block.getBody().getProposer_slashings()).isEqualTo(proposerSlashings);
    assertThat(block.getBody().getVoluntary_exits()).isEqualTo(voluntaryExits);
    assertThat(block.getBody().getGraffiti()).isEqualTo(graffiti);
  }
}
