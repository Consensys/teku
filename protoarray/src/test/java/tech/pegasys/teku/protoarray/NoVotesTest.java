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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.protoarray.ProtoArrayTestUtil.createProtoArrayForkChoiceStrategy;
import static tech.pegasys.teku.protoarray.ProtoArrayTestUtil.createStoreToManipulateVotes;
import static tech.pegasys.teku.protoarray.ProtoArrayTestUtil.getHash;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.forkchoice.MutableStore;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class NoVotesTest {

  @Test
  void noVotesTest() {
    MutableStore store = createStoreToManipulateVotes();

    ProtoArrayForkChoiceStrategy forkChoice =
        createProtoArrayForkChoiceStrategy(getHash(0), ZERO, ONE, ONE);

    List<UInt64> balances = new ArrayList<>(Collections.nCopies(16, ZERO));

    // Check that the head is the finalized block.
    assertThat(forkChoice.findHead(store, unsigned(1), getHash(0), unsigned(1), balances))
        .isEqualTo(getHash(0));

    // Add block 2
    //
    //         0
    //        /
    //        2
    forkChoice.processBlock(ZERO, getHash(2), getHash(0), Bytes32.ZERO, unsigned(1), unsigned(1));

    // Ensure the head is 2
    //
    //         0
    //        /
    //        2 <- head
    assertThat(forkChoice.findHead(store, unsigned(1), getHash(0), unsigned(1), balances))
        .isEqualTo(getHash(2));

    // Add block 1
    //
    //         0
    //        / \
    //        2  1
    forkChoice.processBlock(ZERO, getHash(0), getHash(1), Bytes32.ZERO, unsigned(1), unsigned(1));

    // Ensure the head is still 2
    //
    //         0
    //        / \
    // head-> 2  1
    assertThat(forkChoice.findHead(store, unsigned(1), getHash(0), unsigned(1), balances))
        .isEqualTo(getHash(2));

    // Add block 3
    //
    //         0
    //        / \
    //        2  1
    //           |
    //           3
    forkChoice.processBlock(ZERO, getHash(3), getHash(1), Bytes32.ZERO, unsigned(1), unsigned(1));

    // Ensure 2 is still the head
    //
    //          0
    //         / \
    // head-> 2  1
    //           |
    //           3
    assertThat(forkChoice.findHead(store, unsigned(1), getHash(0), unsigned(1), balances))
        .isEqualTo(getHash(2));

    // Add block 4
    //
    //         0
    //        / \
    //        2  1
    //        |  |
    //        4  3
    forkChoice.processBlock(ZERO, getHash(4), getHash(2), Bytes32.ZERO, unsigned(1), unsigned(1));

    // Ensure the head is 4.
    //
    //         0
    //        / \
    //        2  1
    //        |  |
    // head-> 4  3
    assertThat(forkChoice.findHead(store, unsigned(1), getHash(0), unsigned(1), balances))
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
    forkChoice.processBlock(ZERO, getHash(5), getHash(4), Bytes32.ZERO, unsigned(2), unsigned(1));

    // Ensure the head is still 4 whilst the justified epoch is 0.
    //
    //         0
    //        / \
    //        2  1
    //        |  |
    // head-> 4  3
    //        |
    //        5
    assertThat(forkChoice.findHead(store, unsigned(1), getHash(0), unsigned(1), balances))
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
    assertThatThrownBy(
            () -> forkChoice.findHead(store, unsigned(1), getHash(5), unsigned(1), balances))
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
    assertThat(forkChoice.findHead(store, unsigned(2), getHash(5), unsigned(1), balances))
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
    forkChoice.processBlock(ZERO, getHash(6), getHash(5), Bytes32.ZERO, unsigned(2), unsigned(1));

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
    assertThat(forkChoice.findHead(store, unsigned(2), getHash(5), unsigned(1), balances))
        .isEqualTo(getHash(6));
  }

  private UInt64 unsigned(final int i) {
    return UInt64.valueOf(i);
  }
}
