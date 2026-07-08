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

package tech.pegasys.teku.statetransition.forkchoice.fastconfirmation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.generator.ChainBuilder;

class FastConfirmationCalculatorTest {

  // Minimal preset: SLOTS_PER_EPOCH == 8
  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final ReadOnlyStore store = mock(ReadOnlyStore.class);
  private final ReadOnlyForkChoiceStrategy forkChoice = mock(ReadOnlyForkChoiceStrategy.class);

  // A canonical linear chain where block at index i has slot i and parent = chain(i - 1).
  private final List<Bytes32> chain = new ArrayList<>();
  private final Map<Bytes32, Integer> slotByRoot = new HashMap<>();

  @BeforeEach
  void setUp() {
    when(store.getForkChoiceStrategy()).thenReturn(forkChoice);
  }

  @Test
  void shouldReturnBlockSlotAndEpoch() {
    buildLinearChain(12);
    final FastConfirmationCalculator calculator = calculator(chain.get(11), 11);

    assertThat(calculator.getBlockSlot(chain.get(10))).isEqualTo(UInt64.valueOf(10));
    // slot 10 -> epoch 1 with SLOTS_PER_EPOCH == 8
    assertThat(calculator.getBlockEpoch(chain.get(10))).isEqualTo(UInt64.ONE);
    assertThat(calculator.getBlockEpoch(chain.get(7))).isEqualTo(UInt64.ZERO);
  }

  @Test
  void shouldCollectAncestorRootsDownToButExcludingTerminal() {
    buildLinearChain(6);
    final FastConfirmationCalculator calculator = calculator(chain.get(5), 5);

    // Ancestors of block 5 down to (excluding) block 2, oldest first.
    assertThat(calculator.getAncestorRoots(chain.get(5), chain.get(2)))
        .containsExactly(chain.get(3), chain.get(4), chain.get(5));
  }

  @Test
  void shouldReturnEmptyAncestorRootsWhenTerminalNotInChain() {
    buildLinearChain(6);
    // A terminal block that exists (has a slot) but is not on the chain of block 5.
    final Bytes32 sideTerminal = Bytes32.random();
    when(forkChoice.blockSlot(sideTerminal)).thenReturn(Optional.of(UInt64.valueOf(3)));
    final FastConfirmationCalculator calculator = calculator(chain.get(5), 5);

    assertThat(calculator.getAncestorRoots(chain.get(5), sideTerminal)).isEmpty();
  }

  @Test
  void shouldResolveIsAncestor() {
    buildLinearChain(6);
    final FastConfirmationCalculator calculator = calculator(chain.get(5), 5);

    assertThat(calculator.isAncestor(chain.get(5), chain.get(2))).isTrue();
    // A block is its own ancestor
    assertThat(calculator.isAncestor(chain.get(3), chain.get(3))).isTrue();
    // The descendant/ancestor relationship is not symmetric
    assertThat(calculator.isAncestor(chain.get(2), chain.get(5))).isFalse();
    // An unknown descendant is not a descendant of anything
    assertThat(calculator.isAncestor(Bytes32.random(), chain.get(2))).isFalse();
  }

  @Test
  void shouldComputeCheckpointForBlockAndCurrentTarget() {
    buildLinearChain(11);
    // currentSlot 10 -> currentEpoch 1
    final FastConfirmationCalculator calculator = calculator(chain.get(10), 10);

    // Checkpoint block for epoch 0 is the block at the epoch-0 start slot (0).
    assertThat(calculator.getCheckpointForBlock(chain.get(5), UInt64.ZERO))
        .isEqualTo(new Checkpoint(UInt64.ZERO, chain.get(0)));

    // Current target = checkpoint for the head at the current epoch (1), start slot 8.
    assertThat(calculator.getCurrentTarget()).isEqualTo(new Checkpoint(UInt64.ONE, chain.get(8)));
  }

  @Test
  void shouldUseUnrealizedJustificationAsVotingSourceForPriorEpochBlock() {
    buildLinearChain(11);
    final Checkpoint realized = checkpoint(0);
    final Checkpoint unrealized = checkpoint(1);
    setCheckpoints(chain.get(5), realized, unrealized);
    // currentSlot 10 -> currentEpoch 1; block 5 is in epoch 0 (prior epoch)
    final FastConfirmationCalculator calculator = calculator(chain.get(10), 10);

    assertThat(calculator.getVotingSource(chain.get(5))).isEqualTo(unrealized);
    assertThat(calculator.getUnrealizedJustification(chain.get(5))).isEqualTo(unrealized);
  }

  @Test
  void shouldUseRealizedJustificationAsVotingSourceForCurrentEpochBlock() {
    buildLinearChain(11);
    final Checkpoint realized = checkpoint(1);
    final Checkpoint unrealized = checkpoint(2);
    setCheckpoints(chain.get(9), realized, unrealized);
    // currentSlot 10 -> currentEpoch 1; block 9 is in epoch 1 (current epoch)
    final FastConfirmationCalculator calculator = calculator(chain.get(10), 10);

    assertThat(calculator.getVotingSource(chain.get(9))).isEqualTo(realized);
  }

  @Test
  void shouldReturnHeadStateAsPulledUpWhenAlreadyInCurrentEpoch() {
    final BeaconState headState = genesisState();
    // currentSlot 0 -> currentEpoch 0 == head state epoch, so no pull-up occurs.
    final FastConfirmationCalculator calculator = calculatorWithHeadState(headState, 0);

    assertThat(calculator.getPulledUpHeadState()).isSameAs(headState);
  }

  @Test
  void shouldPullUpHeadStateWhenBehindCurrentEpoch() {
    final BeaconState headState = genesisState();
    // currentSlot 8 -> currentEpoch 1 > head state epoch 0, so pull up to slot 8.
    final FastConfirmationCalculator calculator = calculatorWithHeadState(headState, 8);

    final BeaconState pulledUp = calculator.getPulledUpHeadState();
    assertThat(pulledUp.getSlot()).isEqualTo(UInt64.valueOf(8));
    // Memoized: repeated reads return the same instance.
    assertThat(calculator.getPulledUpHeadState()).isSameAs(pulledUp);
  }

  @Test
  void shouldUnionAllSlotCommitteesToTheActiveValidatorSetOverAnEpoch() {
    final BeaconState headState = genesisState();
    final FastConfirmationCalculator calculator = calculatorWithHeadState(headState, 0);

    final IntSet allCommitteeMembers = new IntOpenHashSet();
    for (int slot = 0; slot < spec.getSlotsPerEpoch(UInt64.ZERO); slot++) {
      allCommitteeMembers.addAll(calculator.getSlotCommittee(UInt64.valueOf(slot)));
    }

    // Every active validator is assigned to exactly one committee per epoch, so the union of all
    // slot committees over epoch 0 must be exactly the active validator set.
    final IntSet activeValidators =
        new IntOpenHashSet(spec.getActiveValidatorIndices(headState, UInt64.ZERO));
    assertThat(allCommitteeMembers).isEqualTo(activeValidators);
    assertThat(calculator.getSlotCommittee(UInt64.ZERO)).isNotEmpty();
  }

  private FastConfirmationCalculator calculator(final Bytes32 head, final long currentSlot) {
    return new FastConfirmationCalculator(
        spec, store, placeholderStates(), head, UInt64.valueOf(currentSlot));
  }

  private FastConfirmationCalculator calculatorWithHeadState(
      final BeaconState headState, final long currentSlot) {
    final FastConfirmationStates states =
        new FastConfirmationStates(Optional.empty(), mock(BeaconState.class), headState);
    return new FastConfirmationCalculator(
        spec, store, states, Bytes32.random(), UInt64.valueOf(currentSlot));
  }

  private FastConfirmationStates placeholderStates() {
    return new FastConfirmationStates(
        Optional.empty(), mock(BeaconState.class), mock(BeaconState.class));
  }

  private BeaconState genesisState() {
    return ChainBuilder.create(spec).generateGenesis().getState();
  }

  private void buildLinearChain(final int length) {
    for (int slot = 0; slot < length; slot++) {
      final Bytes32 root = Bytes32.random();
      chain.add(root);
      slotByRoot.put(root, slot);
    }
    for (int slot = 0; slot < length; slot++) {
      final Bytes32 root = chain.get(slot);
      when(forkChoice.blockSlot(root)).thenReturn(Optional.of(UInt64.valueOf(slot)));
      final Bytes32 parent = slot > 0 ? chain.get(slot - 1) : Bytes32.ZERO;
      when(forkChoice.blockParentRoot(root)).thenReturn(Optional.of(parent));
    }
    // get_ancestor(root, slot): walk up the parent chain until reaching a block at or before slot.
    when(forkChoice.getAncestor(any(), any()))
        .thenAnswer(
            invocation -> {
              final Bytes32 root = invocation.getArgument(0);
              final UInt64 targetSlot = invocation.getArgument(1);
              return ancestorIndex(root, targetSlot).map(chain::get);
            });
    // Node-based ancestry used by ForkChoiceUtil.isAncestor.
    when(forkChoice.getAncestorNode(any(), any()))
        .thenAnswer(
            invocation -> {
              final ForkChoiceNode node = invocation.getArgument(0);
              final UInt64 targetSlot = invocation.getArgument(1);
              return ancestorIndex(node.blockRoot(), targetSlot)
                  .map(chain::get)
                  .map(ForkChoiceNode::createBase);
            });
  }

  private Optional<Integer> ancestorIndex(final Bytes32 root, final UInt64 targetSlot) {
    final Integer index = slotByRoot.get(root);
    if (index == null) {
      return Optional.empty();
    }
    int i = index;
    while (UInt64.valueOf(i).isGreaterThan(targetSlot)) {
      i--;
    }
    return Optional.of(i);
  }

  private void setCheckpoints(
      final Bytes32 root, final Checkpoint justified, final Checkpoint unrealizedJustified) {
    final Checkpoint zero = new Checkpoint(UInt64.ZERO, Bytes32.ZERO);
    final ProtoNodeData data = mock(ProtoNodeData.class);
    when(data.getCheckpoints())
        .thenReturn(new BlockCheckpoints(justified, zero, unrealizedJustified, zero));
    when(forkChoice.getBlockData(root)).thenReturn(Optional.of(data));
  }

  private Checkpoint checkpoint(final int epoch) {
    return new Checkpoint(UInt64.valueOf(epoch), Bytes32.random());
  }
}
