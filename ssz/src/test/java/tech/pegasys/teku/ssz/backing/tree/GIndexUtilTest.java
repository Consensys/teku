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

package tech.pegasys.teku.ssz.backing.tree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.ssz.backing.tree.GIndexUtil.LEFTMOST_G_INDEX;
import static tech.pegasys.teku.ssz.backing.tree.GIndexUtil.NodeRelation.Left;
import static tech.pegasys.teku.ssz.backing.tree.GIndexUtil.NodeRelation.Predecessor;
import static tech.pegasys.teku.ssz.backing.tree.GIndexUtil.NodeRelation.Right;
import static tech.pegasys.teku.ssz.backing.tree.GIndexUtil.NodeRelation.Same;
import static tech.pegasys.teku.ssz.backing.tree.GIndexUtil.NodeRelation.Successor;
import static tech.pegasys.teku.ssz.backing.tree.GIndexUtil.RIGHTMOST_G_INDEX;
import static tech.pegasys.teku.ssz.backing.tree.GIndexUtil.SELF_G_INDEX;
import static tech.pegasys.teku.ssz.backing.tree.GIndexUtil.gIdxChildGIndex;
import static tech.pegasys.teku.ssz.backing.tree.GIndexUtil.gIdxCompare;
import static tech.pegasys.teku.ssz.backing.tree.GIndexUtil.gIdxGetChildIndex;
import static tech.pegasys.teku.ssz.backing.tree.GIndexUtil.gIdxGetDepth;
import static tech.pegasys.teku.ssz.backing.tree.GIndexUtil.gIdxGetParent;
import static tech.pegasys.teku.ssz.backing.tree.GIndexUtil.gIdxGetRelativeGIndex;
import static tech.pegasys.teku.ssz.backing.tree.GIndexUtil.gIdxIsSelf;
import static tech.pegasys.teku.ssz.backing.tree.GIndexUtil.gIdxLeftGIndex;
import static tech.pegasys.teku.ssz.backing.tree.GIndexUtil.gIdxLeftmostFrom;
import static tech.pegasys.teku.ssz.backing.tree.GIndexUtil.gIdxRightGIndex;
import static tech.pegasys.teku.ssz.backing.tree.GIndexUtil.gIdxRightmostFrom;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.ssz.backing.tree.GIndexUtil.NodeRelation;

public class GIndexUtilTest {

  static final long INVALID_G_INDEX = 0L;

  static Stream<Arguments> compareCases() {
    return Stream.of(
        Arguments.of(SELF_G_INDEX, SELF_G_INDEX, Same),
        Arguments.of(Long.MAX_VALUE, Long.MAX_VALUE, Same),
        Arguments.of(Long.MIN_VALUE, Long.MIN_VALUE, Same),
        Arguments.of(LEFTMOST_G_INDEX, LEFTMOST_G_INDEX, Same),
        Arguments.of(RIGHTMOST_G_INDEX, RIGHTMOST_G_INDEX, Same),
        Arguments.of(0b1L, 0b11L, Predecessor),
        Arguments.of(0b1L, 0b10L, Predecessor),
        Arguments.of(0b10L, 0b11L, Left),
        Arguments.of(0b1011L, 0b110L, Left),
        Arguments.of(0b1010L, 0b110L, Left),
        Arguments.of(0b1010L, 0b111L, Left),
        Arguments.of(0b1011L, 0b1100L, Left),
        Arguments.of(0b1011L, 0b101L, Successor),
        Arguments.of(0b10L, RIGHTMOST_G_INDEX, Left),
        Arguments.of(0b11L, RIGHTMOST_G_INDEX, Predecessor),
        Arguments.of(0b10L, LEFTMOST_G_INDEX, Predecessor),
        Arguments.of(0b11L, LEFTMOST_G_INDEX, Right),
        Arguments.of(0b1L, Long.MAX_VALUE, Predecessor),
        Arguments.of(0b10L, Long.MAX_VALUE, Left),
        Arguments.of(0b11L, Long.MAX_VALUE, Predecessor),
        Arguments.of(LEFTMOST_G_INDEX, SELF_G_INDEX, Successor),
        Arguments.of(LEFTMOST_G_INDEX, LEFTMOST_G_INDEX + 1, Left),
        Arguments.of(INVALID_G_INDEX, 0b1L, null),
        Arguments.of(0b1L, INVALID_G_INDEX, null));
  }

  @ParameterizedTest
  @MethodSource("compareCases")
  void testCompare(long idx1, long idx2, NodeRelation expected) {
    if (expected != null) {
      assertThat(gIdxCompare(idx1, idx2)).isEqualTo(expected);
      assertThat(gIdxCompare(idx2, idx1)).isEqualTo(expected.inverse());
    } else {
      assertThatThrownBy(() -> gIdxCompare(idx1, idx2));
    }
  }

  @Test
  void testIsSelf() {
    assertThat(gIdxIsSelf(SELF_G_INDEX)).isTrue();
    assertThat(gIdxIsSelf(0b10)).isFalse();
    assertThat(gIdxIsSelf(LEFTMOST_G_INDEX)).isFalse();
    assertThat(gIdxIsSelf(RIGHTMOST_G_INDEX)).isFalse();
  }

  @Test
  void testGetDepth() {
    assertThat(gIdxGetDepth(SELF_G_INDEX)).isEqualTo(0);
    assertThat(gIdxGetDepth(0b10)).isEqualTo(1);
    assertThat(gIdxGetDepth(0b11)).isEqualTo(1);
    assertThat(gIdxGetDepth(0b100)).isEqualTo(2);
    assertThat(gIdxGetDepth(0b101)).isEqualTo(2);
    assertThat(gIdxGetDepth(0b111)).isEqualTo(2);
    assertThat(gIdxGetDepth(LEFTMOST_G_INDEX)).isEqualTo(63);
    assertThat(gIdxGetDepth(RIGHTMOST_G_INDEX)).isEqualTo(63);
    assertThat(gIdxGetDepth(gIdxLeftmostFrom(0b10010))).isEqualTo(63);
    assertThatThrownBy(() -> gIdxGetDepth(INVALID_G_INDEX));
  }

  @Test
  void testConstants() {
    assertThat(SELF_G_INDEX).isEqualTo(1L);
    assertThat(LEFTMOST_G_INDEX).isEqualTo(Long.MIN_VALUE);
    assertThat(RIGHTMOST_G_INDEX).isEqualTo(-1L);
  }

  @Test
  void testLeftRightGIndex() {
    assertThat(gIdxLeftGIndex(SELF_G_INDEX)).isEqualTo(0b10L);
    assertThat(gIdxRightGIndex(SELF_G_INDEX)).isEqualTo(0b11L);
    assertThat(gIdxLeftGIndex(gIdxGetParent(LEFTMOST_G_INDEX))).isEqualTo(LEFTMOST_G_INDEX);
    assertThat(gIdxRightGIndex(gIdxGetParent(RIGHTMOST_G_INDEX))).isEqualTo(RIGHTMOST_G_INDEX);
    assertThat(gIdxLeftGIndex(0b10L)).isEqualTo(0b100L);
    assertThat(gIdxRightGIndex(0b10L)).isEqualTo(0b101L);
    assertThat(gIdxLeftGIndex(0b11L)).isEqualTo(0b110L);
    assertThat(gIdxRightGIndex(0b11L)).isEqualTo(0b111L);
    assertThatThrownBy(() -> gIdxLeftGIndex(INVALID_G_INDEX));
    assertThatThrownBy(() -> gIdxLeftGIndex(LEFTMOST_G_INDEX));
    assertThatThrownBy(() -> gIdxLeftGIndex(RIGHTMOST_G_INDEX));
  }

  @Test
  void testChildGIndex() {
    assertThat(gIdxChildGIndex(SELF_G_INDEX, 0, 0)).isEqualTo(SELF_G_INDEX);
    assertThat(gIdxChildGIndex(LEFTMOST_G_INDEX, 0, 0)).isEqualTo(LEFTMOST_G_INDEX);
    assertThat(gIdxChildGIndex(RIGHTMOST_G_INDEX, 0, 0)).isEqualTo(RIGHTMOST_G_INDEX);
    assertThat(gIdxChildGIndex(SELF_G_INDEX, 0, 1)).isEqualTo(0b10L);
    assertThat(gIdxChildGIndex(SELF_G_INDEX, 1, 1)).isEqualTo(0b11L);
    assertThat(gIdxChildGIndex(SELF_G_INDEX, 0, 2)).isEqualTo(0b100L);
    assertThat(gIdxChildGIndex(SELF_G_INDEX, 1, 2)).isEqualTo(0b101L);
    assertThat(gIdxChildGIndex(SELF_G_INDEX, 2, 2)).isEqualTo(0b110L);
    assertThat(gIdxChildGIndex(SELF_G_INDEX, 3, 2)).isEqualTo(0b111L);
    assertThat(gIdxChildGIndex(0b100L, 0, 2)).isEqualTo(0b10000L);
    assertThat(gIdxChildGIndex(0b100L, 1, 2)).isEqualTo(0b10001L);
    assertThat(gIdxChildGIndex(0b100L, 2, 2)).isEqualTo(0b10010L);
    assertThat(gIdxChildGIndex(0b100L, 3, 2)).isEqualTo(0b10011L);

    assertThatThrownBy(() -> gIdxChildGIndex(INVALID_G_INDEX, 0, 2));
    assertThatThrownBy(() -> gIdxChildGIndex(SELF_G_INDEX, 4, 2));
    assertThatThrownBy(() -> gIdxChildGIndex(LEFTMOST_G_INDEX, 0, 1));
    assertThatThrownBy(() -> gIdxChildGIndex(RIGHTMOST_G_INDEX, 0, 1));
    assertThatThrownBy(() -> gIdxChildGIndex(gIdxGetParent(LEFTMOST_G_INDEX), 0, 2));
  }

  @Test
  void testLeftRightmost() {
    assertThat(gIdxLeftmostFrom(SELF_G_INDEX)).isEqualTo(LEFTMOST_G_INDEX);
    assertThat(gIdxRightmostFrom(SELF_G_INDEX)).isEqualTo(RIGHTMOST_G_INDEX);
    assertThat(gIdxLeftmostFrom(0b10)).isEqualTo(LEFTMOST_G_INDEX);
    assertThat(gIdxRightmostFrom(0b11)).isEqualTo(RIGHTMOST_G_INDEX);
    assertThat(gIdxLeftmostFrom(0b11)).isEqualTo(0b11L << 62);
    assertThat(gIdxRightmostFrom(0b10))
        .isEqualTo(0b1011111111111111_1111111111111111_1111111111111111_1111111111111111L);

    assertThat(gIdxLeftmostFrom(LEFTMOST_G_INDEX)).isEqualTo(LEFTMOST_G_INDEX);
    assertThat(gIdxRightmostFrom(LEFTMOST_G_INDEX)).isEqualTo(LEFTMOST_G_INDEX);
    assertThat(gIdxLeftmostFrom(RIGHTMOST_G_INDEX)).isEqualTo(RIGHTMOST_G_INDEX);
    assertThat(gIdxRightmostFrom(RIGHTMOST_G_INDEX)).isEqualTo(RIGHTMOST_G_INDEX);

    assertThatThrownBy(() -> gIdxLeftmostFrom(INVALID_G_INDEX));
    assertThatThrownBy(() -> gIdxRightmostFrom(INVALID_G_INDEX));
  }

  @Test
  void testGetChildIndex() {
    assertThat(gIdxGetChildIndex(SELF_G_INDEX, 0)).isEqualTo(0);
    assertThat(gIdxGetChildIndex(LEFTMOST_G_INDEX, 0)).isEqualTo(0);
    assertThat(gIdxGetChildIndex(RIGHTMOST_G_INDEX, 0)).isEqualTo(0);
    assertThat(gIdxGetChildIndex(LEFTMOST_G_INDEX, 22)).isEqualTo(0);
    assertThat(gIdxGetChildIndex(LEFTMOST_G_INDEX, 63)).isEqualTo(0);
    assertThat(gIdxGetChildIndex(RIGHTMOST_G_INDEX, 1)).isEqualTo(1);
    assertThat(gIdxGetChildIndex(RIGHTMOST_G_INDEX, 2)).isEqualTo(3);
    assertThat(gIdxGetChildIndex(RIGHTMOST_G_INDEX, 3)).isEqualTo(7);
    assertThat(gIdxGetChildIndex(RIGHTMOST_G_INDEX, 22)).isEqualTo((1 << 22) - 1);
    assertThat(gIdxGetChildIndex(RIGHTMOST_G_INDEX, 63)).isEqualTo(RIGHTMOST_G_INDEX);
    assertThat(gIdxGetChildIndex(0b1100, 0)).isEqualTo(0);
    assertThat(gIdxGetChildIndex(0b1100, 1)).isEqualTo(1);
    assertThat(gIdxGetChildIndex(0b1100, 2)).isEqualTo(2);
    assertThat(gIdxGetChildIndex(0b1100, 3)).isEqualTo(4);
    assertThat(gIdxGetChildIndex(0b1000, 1)).isEqualTo(0);
    assertThat(gIdxGetChildIndex(0b1000, 2)).isEqualTo(0);
    assertThat(gIdxGetChildIndex(0b1000, 3)).isEqualTo(0);

    assertThatThrownBy(() -> gIdxGetChildIndex(INVALID_G_INDEX, 0));
    assertThatThrownBy(() -> gIdxGetChildIndex(INVALID_G_INDEX, 1));
    assertThatThrownBy(() -> gIdxGetChildIndex(SELF_G_INDEX, 1));
    assertThatThrownBy(() -> gIdxGetChildIndex(SELF_G_INDEX, 2));
    assertThatThrownBy(() -> gIdxGetChildIndex(SELF_G_INDEX, -1));
    assertThatThrownBy(() -> gIdxGetChildIndex(RIGHTMOST_G_INDEX, 64));
  }

  @Test
  void testRelativeGIndex() {
    assertThat(gIdxGetRelativeGIndex(SELF_G_INDEX, 0)).isEqualTo(SELF_G_INDEX);
    assertThat(gIdxGetRelativeGIndex(LEFTMOST_G_INDEX, 0)).isEqualTo(LEFTMOST_G_INDEX);
    assertThat(gIdxGetRelativeGIndex(RIGHTMOST_G_INDEX, 0)).isEqualTo(RIGHTMOST_G_INDEX);
    assertThat(gIdxGetRelativeGIndex(RIGHTMOST_G_INDEX, 62)).isEqualTo(0b11L);
    assertThat(gIdxGetRelativeGIndex(0b100100111, 3)).isEqualTo(0b100111L);
    assertThat(gIdxGetRelativeGIndex(0b10, 1)).isEqualTo(SELF_G_INDEX);
    assertThat(gIdxGetRelativeGIndex(0b1011, 0)).isEqualTo(0b1011);
    assertThat(gIdxGetRelativeGIndex(0b1011, 1)).isEqualTo(0b111);
    assertThat(gIdxGetRelativeGIndex(0b1011, 2)).isEqualTo(0b11);
    assertThat(gIdxGetRelativeGIndex(0b1011, 3)).isEqualTo(SELF_G_INDEX);

    assertThatThrownBy(() -> gIdxGetRelativeGIndex(SELF_G_INDEX, 1));
    assertThatThrownBy(() -> gIdxGetRelativeGIndex(0b10, 2));
    assertThatThrownBy(() -> gIdxGetRelativeGIndex(0b10, -1));
    assertThatThrownBy(() -> gIdxGetRelativeGIndex(0b10, 64));
  }
}
