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
import java.util.Collections;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;

public class NoVotesTest {

  @Test
  void noVotesTest() {
    ProtoArrayForkChoiceStrategy forkChoice =
        createProtoArrayForkChoiceStrategy(getHash(0), ZERO, ONE, ONE);

    List<UnsignedLong> balances = new ArrayList<>(Collections.nCopies(16, ZERO));

    // Check that the head is the finalized block.
    assertThat(processHead(forkChoice, valueOf(1), getHash(0), valueOf(1), balances))
        .isEqualTo(getHash(0));

    // Add block 2
    //
    //         0
    //        /
    //        2
    ProtoArrayForkChoiceStrategyUpdater updater = forkChoice.updater();
    updater.processBlock(ZERO, getHash(2), getHash(0), Bytes32.ZERO, valueOf(1), valueOf(1));
    updater.commit();

    // Ensure the head is 2
    //
    //         0
    //        /
    //        2 <- head
    assertThat(processHead(forkChoice, valueOf(1), getHash(0), valueOf(1), balances))
        .isEqualTo(getHash(2));

    // Add block 1
    //
    //         0
    //        / \
    //        2  1
    updater = forkChoice.updater();
    updater.processBlock(ZERO, getHash(0), getHash(1), Bytes32.ZERO, valueOf(1), valueOf(1));
    updater.commit();

    // Ensure the head is still 2
    //
    //         0
    //        / \
    // head-> 2  1
    assertThat(processHead(forkChoice, valueOf(1), getHash(0), valueOf(1), balances))
        .isEqualTo(getHash(2));

    // Add block 3
    //
    //         0
    //        / \
    //        2  1
    //           |
    //           3
    updater = forkChoice.updater();
    updater.processBlock(ZERO, getHash(3), getHash(1), Bytes32.ZERO, valueOf(1), valueOf(1));
    updater.commit();

    // Ensure 2 is still the head
    //
    //          0
    //         / \
    // head-> 2  1
    //           |
    //           3
    assertThat(processHead(forkChoice, valueOf(1), getHash(0), valueOf(1), balances))
        .isEqualTo(getHash(2));

    // Add block 4
    //
    //         0
    //        / \
    //        2  1
    //        |  |
    //        4  3
    updater = forkChoice.updater();
    updater.processBlock(ZERO, getHash(4), getHash(2), Bytes32.ZERO, valueOf(1), valueOf(1));
    updater.commit();

    // Ensure the head is 4.
    //
    //         0
    //        / \
    //        2  1
    //        |  |
    // head-> 4  3
    assertThat(processHead(forkChoice, valueOf(1), getHash(0), valueOf(1), balances))
        .isEqualTo(getHash(4));

    // Add block 5 with a justified epoch of 2
    //
    //         0
    //        / \
    //        2  1
    //        |  |
    //        4  3
    //        |
    //        5 <- justified epoch = 2
    updater = forkChoice.updater();
    updater.processBlock(ZERO, getHash(5), getHash(4), Bytes32.ZERO, valueOf(2), valueOf(1));
    updater.commit();

    // Ensure the head is still 4 whilst the justified epoch is 0.
    //
    //         0
    //        / \
    //        2  1
    //        |  |
    // head-> 4  3
    //        |
    //        5
    assertThat(processHead(forkChoice, valueOf(1), getHash(0), valueOf(1), balances))
        .isEqualTo(getHash(4));

    // Ensure there is an error when starting from a block that has the wrong justified epoch.
    //
    //      0
    //     / \
    //     2  1
    //     |  |
    //     4  3
    //     |
    //     5 <- starting from 5 with justified epoch 0 should error.
    assertThatThrownBy(() -> processHead(forkChoice, valueOf(1), getHash(5), valueOf(1), balances))
        .hasMessage("ProtoArray: Best node is not viable for head");

    // Set the justified epoch to 2 and the start block to 5 and ensure 5 is the head.
    //
    //      0
    //     / \
    //     2  1
    //     |  |
    //     4  3
    //     |
    //     5 <- head
    assertThat(processHead(forkChoice, valueOf(2), getHash(5), valueOf(1), balances))
        .isEqualTo(getHash(5));

    // Add block 6
    //
    //      0
    //     / \
    //     2  1
    //     |  |
    //     4  3
    //     |
    //     5
    //     |
    //     6
    updater = forkChoice.updater();
    updater.processBlock(ZERO, getHash(6), getHash(5), Bytes32.ZERO, valueOf(2), valueOf(1));
    updater.commit();

    // Ensure 6 is the head
    //
    //      0
    //     / \
    //     2  1
    //     |  |
    //     4  3
    //     |
    //     5
    //     |
    //     6 <- head
    assertThat(processHead(forkChoice, valueOf(2), getHash(5), valueOf(1), balances))
        .isEqualTo(getHash(6));
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
