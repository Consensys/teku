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

package tech.pegasys.teku.statetransition.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus.PAYLOAD_STATUS_FULL;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeValidationStatus;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class GloasExecutionPayloadBidCircuitBreakerTest {

  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createMainnetGloas());
  private final ReadOnlyForkChoiceStrategy forkChoiceStrategy =
      mock(ReadOnlyForkChoiceStrategy.class);
  private final Bytes32 parentRoot = dataStructureUtil.randomBytes32();

  @Test
  public void shouldEngageWhenForkChoiceStrategyIsUnavailable() {
    final GloasExecutionPayloadBidCircuitBreaker circuitBreaker =
        new GloasExecutionPayloadBidCircuitBreaker(4, 1, 1, Optional::empty);

    assertThat(circuitBreaker.isEngaged(parentRoot, stateAtSlot(10))).isTrue();
  }

  @Test
  public void shouldEngageWhenParentRootIsUnavailable() {
    final GloasExecutionPayloadBidCircuitBreaker circuitBreaker = createCircuitBreaker(4, 1, 1);
    when(forkChoiceStrategy.contains(parentRoot)).thenReturn(false);

    assertThat(circuitBreaker.isEngaged(parentRoot, stateAtSlot(10))).isTrue();
  }

  @Test
  public void shouldNotEngageWhenAncestorPayloadsAreAvailable() {
    final GloasExecutionPayloadBidCircuitBreaker circuitBreaker = createCircuitBreaker(4, 1, 1);
    setAncestor(6, availablePayload());
    setAncestor(7, availablePayload());
    setAncestor(8, availablePayload());
    setAncestor(9, availablePayload());

    assertThat(circuitBreaker.isEngaged(parentRoot, stateAtSlot(10))).isFalse();
  }

  @Test
  public void shouldEngageWhenUnavailablePayloadsExceedAllowedFaults() {
    final GloasExecutionPayloadBidCircuitBreaker circuitBreaker = createCircuitBreaker(4, 1, 3);
    setAncestor(6, availablePayload());
    setAncestor(7, unavailablePayload());
    setAncestor(8, optimisticPayload());
    setAncestor(9, availablePayload());

    assertThat(circuitBreaker.isEngaged(parentRoot, stateAtSlot(10))).isTrue();
  }

  @Test
  public void shouldEngageWhenConsecutiveUnavailablePayloadsExceedAllowedFaults() {
    final GloasExecutionPayloadBidCircuitBreaker circuitBreaker = createCircuitBreaker(4, 3, 1);
    setAncestor(6, availablePayload());
    setAncestor(7, availablePayload());
    setAncestor(8, unavailablePayload());
    setAncestor(9, unavailablePayload());

    assertThat(circuitBreaker.isEngaged(parentRoot, stateAtSlot(10))).isTrue();
  }

  @Test
  public void shouldNotCountSkippedConsensusSlotsAsPayloadFaults() {
    final GloasExecutionPayloadBidCircuitBreaker circuitBreaker = createCircuitBreaker(4, 0, 0);
    final Bytes32 blockAtSlot7 = availablePayload();
    final Bytes32 blockAtSlot9 = availablePayload();
    when(forkChoiceStrategy.getAncestor(parentRoot, UInt64.valueOf(6)))
        .thenReturn(Optional.empty());
    when(forkChoiceStrategy.getAncestor(parentRoot, UInt64.valueOf(7)))
        .thenReturn(Optional.of(blockAtSlot7));
    when(forkChoiceStrategy.getAncestor(parentRoot, UInt64.valueOf(8)))
        .thenReturn(Optional.of(blockAtSlot7));
    when(forkChoiceStrategy.getAncestor(parentRoot, UInt64.valueOf(9)))
        .thenReturn(Optional.of(blockAtSlot9));
    setBlockSlot(blockAtSlot7, 7);
    setBlockSlot(blockAtSlot9, 9);

    assertThat(circuitBreaker.isEngaged(parentRoot, stateAtSlot(10))).isFalse();
  }

  @Test
  public void shouldEngageWhenAncestorSlotIsUnavailable() {
    final GloasExecutionPayloadBidCircuitBreaker circuitBreaker = createCircuitBreaker(4, 1, 1);
    final Bytes32 blockAtSlot9 = availablePayload();
    when(forkChoiceStrategy.getAncestor(parentRoot, UInt64.valueOf(9)))
        .thenReturn(Optional.of(blockAtSlot9));
    when(forkChoiceStrategy.blockSlot(blockAtSlot9)).thenReturn(Optional.empty());

    assertThat(circuitBreaker.isEngaged(parentRoot, stateAtSlot(10))).isTrue();
  }

  private GloasExecutionPayloadBidCircuitBreaker createCircuitBreaker(
      final int faultInspectionWindow,
      final int allowedFaults,
      final int consecutiveAllowedFaults) {
    when(forkChoiceStrategy.contains(parentRoot)).thenReturn(true);
    return new GloasExecutionPayloadBidCircuitBreaker(
        faultInspectionWindow,
        allowedFaults,
        consecutiveAllowedFaults,
        () -> Optional.of(forkChoiceStrategy));
  }

  private Bytes32 availablePayload() {
    return payload(ProtoNodeValidationStatus.VALID);
  }

  private Bytes32 optimisticPayload() {
    return payload(ProtoNodeValidationStatus.OPTIMISTIC);
  }

  private Bytes32 unavailablePayload() {
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    when(forkChoiceStrategy.getBlockData(blockRoot, PAYLOAD_STATUS_FULL))
        .thenReturn(Optional.empty());
    return blockRoot;
  }

  private Bytes32 payload(final ProtoNodeValidationStatus validationStatus) {
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    when(forkChoiceStrategy.getBlockData(blockRoot, PAYLOAD_STATUS_FULL))
        .thenReturn(Optional.of(nodeData(blockRoot, validationStatus)));
    return blockRoot;
  }

  private ProtoNodeData nodeData(
      final Bytes32 blockRoot, final ProtoNodeValidationStatus validationStatus) {
    return new ProtoNodeData(
        UInt64.ZERO,
        blockRoot,
        dataStructureUtil.randomBytes32(),
        dataStructureUtil.randomBytes32(),
        UInt64.ZERO,
        dataStructureUtil.randomBytes32(),
        validationStatus,
        mock(BlockCheckpoints.class),
        UInt64.ZERO,
        PAYLOAD_STATUS_FULL);
  }

  private void setAncestor(final int slot, final Bytes32 blockRoot) {
    when(forkChoiceStrategy.getAncestor(parentRoot, UInt64.valueOf(slot)))
        .thenReturn(Optional.of(blockRoot));
    setBlockSlot(blockRoot, slot);
  }

  private void setBlockSlot(final Bytes32 blockRoot, final int slot) {
    when(forkChoiceStrategy.blockSlot(blockRoot)).thenReturn(Optional.of(UInt64.valueOf(slot)));
  }

  private BeaconState stateAtSlot(final int slot) {
    final BeaconState state = mock(BeaconState.class);
    when(state.getSlot()).thenReturn(UInt64.valueOf(slot));
    return state;
  }
}
