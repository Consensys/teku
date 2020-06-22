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

package tech.pegasys.teku.protoarray;

import static com.google.common.primitives.UnsignedLong.ONE;
import static com.google.common.primitives.UnsignedLong.ZERO;
import static com.google.common.primitives.UnsignedLong.valueOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.protoarray.ProtoArrayTestUtil.createProtoArrayForkChoiceStrategy;
import static tech.pegasys.teku.protoarray.ProtoArrayTestUtil.getHash;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;

public class FFGUpdatesTest {

  @Test
  void case1() {
    ProtoArrayForkChoiceStrategy forkChoice =
        createProtoArrayForkChoiceStrategy(getHash(0), ZERO, ONE, ONE);

    List<UnsignedLong> balances = new ArrayList<>(List.of(valueOf(1), valueOf(1)));

    // Ensure that the head starts at the finalized block.
    assertThat(processHead(forkChoice, valueOf(0), getHash(0), valueOf(0), balances))
        .isEqualTo(getHash(0));

    // Build the following tree
    //
    //            0 <- just: 0, fin: 0
    //            |
    //            1 <- just: 0, fin: 0
    //            |
    //            2 <- just: 1, fin: 0
    //            |
    //            3 <- just: 2, fin: 1
    ProtoArrayForkChoiceStrategyUpdater updater = forkChoice.updater();
    updater.processBlock(ONE, getHash(1), getHash(0), Bytes32.ZERO, valueOf(0), valueOf(0));
    updater.processBlock(valueOf(2), getHash(2), getHash(1), Bytes32.ZERO, valueOf(1), valueOf(0));
    updater.processBlock(valueOf(3), getHash(3), getHash(2), Bytes32.ZERO, valueOf(2), valueOf(1));
    updater.commit();
    // Ensure that with justified epoch 0 we find 3
    //
    //            0 <- start
    //            |
    //            1
    //            |
    //            2
    //            |
    //            3 <- head
    assertThat(processHead(forkChoice, valueOf(0), getHash(0), valueOf(0), balances))
        .isEqualTo(getHash(3));

    // Ensure that with justified epoch 1 we find 2
    //
    //            0
    //            |
    //            1
    //            |
    //            2 <- start
    //            |
    //            3 <- head
    assertThat(processHead(forkChoice, valueOf(1), getHash(2), valueOf(0), balances))
        .isEqualTo(getHash(2));

    // Ensure that with justified epoch 2 we find 3
    //
    //            0
    //            |
    //            1
    //            |
    //            2
    //            |
    //            3 <- start + head
    assertThat(processHead(forkChoice, valueOf(2), getHash(3), valueOf(1), balances))
        .isEqualTo(getHash(3));
  }

  @Test
  void case2() {
    ProtoArrayForkChoiceStrategy forkChoice =
        createProtoArrayForkChoiceStrategy(getHash(0), ZERO, ONE, ONE);

    List<UnsignedLong> balances = new ArrayList<>(List.of(valueOf(1), valueOf(1)));

    // Ensure that the head starts at the finalized block.
    assertThat(processHead(forkChoice, valueOf(1), getHash(0), valueOf(1), balances))
        .isEqualTo(getHash(0));

    // Build the following tree.
    //
    //                       0
    //                      / \
    //  just: 0, fin: 0 -> 1   2 <- just: 0, fin: 0
    //                     |   |
    //  just: 1, fin: 0 -> 3   4 <- just: 0, fin: 0
    //                     |   |
    //  just: 1, fin: 0 -> 5   6 <- just: 0, fin: 0
    //                     |   |
    //  just: 1, fin: 0 -> 7   8 <- just: 1, fin: 0
    //                     |   |
    //  just: 2, fin: 0 -> 9  10 <- just: 2, fin: 0

    //  Left branch
    ProtoArrayForkChoiceStrategyUpdater updater = forkChoice.updater();
    updater.processBlock(valueOf(1), getHash(1), getHash(0), Bytes32.ZERO, valueOf(0), valueOf(0));
    updater.processBlock(valueOf(2), getHash(3), getHash(1), Bytes32.ZERO, valueOf(1), valueOf(0));
    updater.processBlock(valueOf(3), getHash(5), getHash(3), Bytes32.ZERO, valueOf(1), valueOf(0));
    updater.processBlock(valueOf(4), getHash(7), getHash(5), Bytes32.ZERO, valueOf(1), valueOf(0));
    updater.processBlock(valueOf(4), getHash(9), getHash(7), Bytes32.ZERO, valueOf(2), valueOf(0));

    //  Right branch
    updater.processBlock(valueOf(1), getHash(2), getHash(0), Bytes32.ZERO, valueOf(0), valueOf(0));
    updater.processBlock(valueOf(2), getHash(4), getHash(2), Bytes32.ZERO, valueOf(0), valueOf(0));
    updater.processBlock(valueOf(3), getHash(6), getHash(4), Bytes32.ZERO, valueOf(0), valueOf(0));
    updater.processBlock(valueOf(4), getHash(8), getHash(6), Bytes32.ZERO, valueOf(1), valueOf(0));
    updater.processBlock(valueOf(4), getHash(10), getHash(8), Bytes32.ZERO, valueOf(2), valueOf(0));

    updater.commit();
    // Ensure that if we start at 0 we find 10 (just: 0, fin: 0).
    //
    //           0  <-- start
    //          / \
    //         1   2
    //         |   |
    //         3   4
    //         |   |
    //         5   6
    //         |   |
    //         7   8
    //         |   |
    //         9  10 <-- head
    assertThat(processHead(forkChoice, valueOf(0), getHash(0), valueOf(0), balances))
        .isEqualTo(getHash(10));

    // Same as above, but with justified epoch 2.
    assertThat(processHead(forkChoice, valueOf(2), getHash(0), valueOf(0), balances))
        .isEqualTo(getHash(10));

    // Same as above, but with justified epoch 3 (should be invalid).
    assertThatThrownBy(() -> processHead(forkChoice, valueOf(3), getHash(0), valueOf(0), balances))
        .hasMessage("ProtoArray: Best node is not viable for head");

    // Add a vote to 1.
    //
    //                 0
    //                / \
    //    +1 vote -> 1   2
    //               |   |
    //               3   4
    //               |   |
    //               5   6
    //               |   |
    //               7   8
    //               |   |
    //               9  10
    updater = forkChoice.updater();
    updater.processAttestation(valueOf(0), getHash(1), valueOf(0));
    updater.commit();

    // Ensure that if we start at 0 we find 9 (just: 0, fin: 0).
    //
    //           0  <-- start
    //          / \
    //         1   2
    //         |   |
    //         3   4
    //         |   |
    //         5   6
    //         |   |
    //         7   8
    //         |   |
    // head -> 9  10
    assertThat(processHead(forkChoice, valueOf(0), getHash(0), valueOf(0), balances))
        .isEqualTo(getHash(9));

    // Same as above but justified epoch 2.
    assertThat(processHead(forkChoice, valueOf(2), getHash(0), valueOf(0), balances))
        .isEqualTo(getHash(9));

    // Same as above but justified epoch 3 (should fail).
    assertThatThrownBy(() -> processHead(forkChoice, valueOf(3), getHash(0), valueOf(0), balances))
        .hasMessage("ProtoArray: Best node is not viable for head");

    // Add a vote to 2.
    //
    //                 0
    //                / \
    //               1   2 <- +1 vote
    //               |   |
    //               3   4
    //               |   |
    //               5   6
    //               |   |
    //               7   8
    //               |   |
    //               9  10
    updater = forkChoice.updater();
    updater.processAttestation(valueOf(1), getHash(2), valueOf(0));
    updater.commit();

    // Ensure that if we start at 0 we find 10 (just: 0, fin: 0).
    //
    //           0  <-- start
    //          / \
    //         1   2
    //         |   |
    //         3   4
    //         |   |
    //         5   6
    //         |   |
    //         7   8
    //         |   |
    //         9  10 <-- head
    assertThat(processHead(forkChoice, valueOf(0), getHash(0), valueOf(0), balances))
        .isEqualTo(getHash(10));

    // Same as above but justified epoch 2.
    assertThat(processHead(forkChoice, valueOf(2), getHash(0), valueOf(0), balances))
        .isEqualTo(getHash(10));

    // Same as above but justified epoch 3 (should fail).
    assertThatThrownBy(() -> processHead(forkChoice, valueOf(3), getHash(0), valueOf(0), balances))
        .hasMessage("ProtoArray: Best node is not viable for head");

    // Ensure that if we start at 1 we find 9 (just: 0, fin: 0).
    //
    //            0
    //           / \
    //  start-> 1   2
    //          |   |
    //          3   4
    //          |   |
    //          5   6
    //          |   |
    //          7   8
    //          |   |
    //  head -> 9  10
    assertThat(processHead(forkChoice, valueOf(0), getHash(1), valueOf(0), balances))
        .isEqualTo(getHash(9));

    // Same as above but justified epoch 2.
    assertThat(processHead(forkChoice, valueOf(2), getHash(1), valueOf(0), balances))
        .isEqualTo(getHash(9));

    // Same as above but justified epoch 3 (should fail).
    assertThatThrownBy(() -> processHead(forkChoice, valueOf(3), getHash(1), valueOf(0), balances))
        .hasMessage("ProtoArray: Best node is not viable for head");

    // Ensure that if we start at 2 we find 10 (just: 0, fin: 0).
    //
    //            0
    //           / \
    //          1   2 <- start
    //          |   |
    //          3   4
    //          |   |
    //          5   6
    //          |   |
    //          7   8
    //          |   |
    //          9  10 <- head
    assertThat(processHead(forkChoice, valueOf(0), getHash(2), valueOf(0), balances))
        .isEqualTo(getHash(10));

    // Same as above but justified epoch 2.
    assertThat(processHead(forkChoice, valueOf(2), getHash(2), valueOf(0), balances))
        .isEqualTo(getHash(10));

    // Same as above but justified epoch 3 (should fail).
    assertThatThrownBy(() -> processHead(forkChoice, valueOf(3), getHash(2), valueOf(0), balances))
        .hasMessage("ProtoArray: Best node is not viable for head");
  }

  private Bytes32 processHead(
      final ProtoArrayForkChoiceStrategy forkChoice,
      final UnsignedLong justifiedEpoch,
      final Bytes32 justifiedRoot,
      final UnsignedLong finalizedEpoch,
      final List<UnsignedLong> justifiedStateBalances) {
    final BeaconState justifiedCheckpointState = mock(BeaconState.class);
    when(justifiedCheckpointState.getBalances())
        .thenReturn(
            SSZList.createMutable(
                justifiedStateBalances, justifiedStateBalances.size(), UnsignedLong.class));

    ProtoArrayForkChoiceStrategyUpdater updater = forkChoice.updater();
    updater.updateHead(
        new Checkpoint(finalizedEpoch, getHash(0)),
        new Checkpoint(justifiedEpoch, justifiedRoot),
        justifiedCheckpointState);
    updater.commit();

    return forkChoice.getHead();
  }
}
