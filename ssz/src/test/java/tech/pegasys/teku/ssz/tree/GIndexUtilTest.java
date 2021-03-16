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

package tech.pegasys.teku.ssz.tree;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.ssz.tree.GIndexUtil.NodeRelation;

public class GIndexUtilTest {

  static final long INVALID_G_INDEX = 0L;

  static Stream<Arguments> compareCases() {
    return Stream.of(
        Arguments.of(GIndexUtil.SELF_G_INDEX, GIndexUtil.SELF_G_INDEX, NodeRelation.Same),
        Arguments.of(Long.MAX_VALUE, Long.MAX_VALUE, NodeRelation.Same),
        Arguments.of(Long.MIN_VALUE, Long.MIN_VALUE, NodeRelation.Same),
        Arguments.of(GIndexUtil.LEFTMOST_G_INDEX, GIndexUtil.LEFTMOST_G_INDEX, NodeRelation.Same),
        Arguments.of(GIndexUtil.RIGHTMOST_G_INDEX, GIndexUtil.RIGHTMOST_G_INDEX, NodeRelation.Same),
        Arguments.of(0b1L, 0b11L, NodeRelation.Predecessor),
        Arguments.of(0b1L, 0b10L, NodeRelation.Predecessor),
        Arguments.of(0b10L, 0b11L, NodeRelation.Left),
        Arguments.of(0b1011L, 0b110L, NodeRelation.Left),
        Arguments.of(0b1010L, 0b110L, NodeRelation.Left),
        Arguments.of(0b1010L, 0b111L, NodeRelation.Left),
        Arguments.of(0b1011L, 0b1100L, NodeRelation.Left),
        Arguments.of(0b1011L, 0b101L, NodeRelation.Successor),
        Arguments.of(0b10L, GIndexUtil.RIGHTMOST_G_INDEX, NodeRelation.Left),
        Arguments.of(0b11L, GIndexUtil.RIGHTMOST_G_INDEX, NodeRelation.Predecessor),
        Arguments.of(0b10L, GIndexUtil.LEFTMOST_G_INDEX, NodeRelation.Predecessor),
        Arguments.of(0b11L, GIndexUtil.LEFTMOST_G_INDEX, NodeRelation.Right),
        Arguments.of(0b1L, Long.MAX_VALUE, NodeRelation.Predecessor),
        Arguments.of(0b10L, Long.MAX_VALUE, NodeRelation.Left),
        Arguments.of(0b11L, Long.MAX_VALUE, NodeRelation.Predecessor),
        Arguments.of(GIndexUtil.LEFTMOST_G_INDEX, GIndexUtil.SELF_G_INDEX, NodeRelation.Successor),
        Arguments.of(
            GIndexUtil.LEFTMOST_G_INDEX, GIndexUtil.LEFTMOST_G_INDEX + 1, NodeRelation.Left),
        Arguments.of(INVALID_G_INDEX, 0b1L, null),
        Arguments.of(0b1L, INVALID_G_INDEX, null));
  }

  @ParameterizedTest
  @MethodSource("compareCases")
  void testCompare(long idx1, long idx2, NodeRelation expected) {
    if (expected != null) {
      Assertions.assertThat(GIndexUtil.gIdxCompare(idx1, idx2)).isEqualTo(expected);
      Assertions.assertThat(GIndexUtil.gIdxCompare(idx2, idx1)).isEqualTo(expected.inverse());
    } else {
      assertThatThrownBy(() -> GIndexUtil.gIdxCompare(idx1, idx2));
    }
  }

  @Test
  void testIsSelf() {
    Assertions.assertThat(GIndexUtil.gIdxIsSelf(GIndexUtil.SELF_G_INDEX)).isTrue();
    Assertions.assertThat(GIndexUtil.gIdxIsSelf(0b10)).isFalse();
    Assertions.assertThat(GIndexUtil.gIdxIsSelf(GIndexUtil.LEFTMOST_G_INDEX)).isFalse();
    Assertions.assertThat(GIndexUtil.gIdxIsSelf(GIndexUtil.RIGHTMOST_G_INDEX)).isFalse();
  }

  @Test
  void testGetDepth() {
    Assertions.assertThat(GIndexUtil.gIdxGetDepth(GIndexUtil.SELF_G_INDEX)).isEqualTo(0);
    Assertions.assertThat(GIndexUtil.gIdxGetDepth(0b10)).isEqualTo(1);
    Assertions.assertThat(GIndexUtil.gIdxGetDepth(0b11)).isEqualTo(1);
    Assertions.assertThat(GIndexUtil.gIdxGetDepth(0b100)).isEqualTo(2);
    Assertions.assertThat(GIndexUtil.gIdxGetDepth(0b101)).isEqualTo(2);
    Assertions.assertThat(GIndexUtil.gIdxGetDepth(0b111)).isEqualTo(2);
    Assertions.assertThat(GIndexUtil.gIdxGetDepth(GIndexUtil.LEFTMOST_G_INDEX)).isEqualTo(63);
    Assertions.assertThat(GIndexUtil.gIdxGetDepth(GIndexUtil.RIGHTMOST_G_INDEX)).isEqualTo(63);
    Assertions.assertThat(GIndexUtil.gIdxGetDepth(GIndexUtil.gIdxLeftmostFrom(0b10010)))
        .isEqualTo(63);
    assertThatThrownBy(() -> GIndexUtil.gIdxGetDepth(INVALID_G_INDEX));
  }

  @Test
  void testConstants() {
    Assertions.assertThat(GIndexUtil.SELF_G_INDEX).isEqualTo(1L);
    Assertions.assertThat(GIndexUtil.LEFTMOST_G_INDEX).isEqualTo(Long.MIN_VALUE);
    Assertions.assertThat(GIndexUtil.RIGHTMOST_G_INDEX).isEqualTo(-1L);
  }

  @Test
  void testLeftRightGIndex() {
    Assertions.assertThat(GIndexUtil.gIdxLeftGIndex(GIndexUtil.SELF_G_INDEX)).isEqualTo(0b10L);
    Assertions.assertThat(GIndexUtil.gIdxRightGIndex(GIndexUtil.SELF_G_INDEX)).isEqualTo(0b11L);
    Assertions.assertThat(
            GIndexUtil.gIdxLeftGIndex(GIndexUtil.gIdxGetParent(GIndexUtil.LEFTMOST_G_INDEX)))
        .isEqualTo(GIndexUtil.LEFTMOST_G_INDEX);
    Assertions.assertThat(
            GIndexUtil.gIdxRightGIndex(GIndexUtil.gIdxGetParent(GIndexUtil.RIGHTMOST_G_INDEX)))
        .isEqualTo(GIndexUtil.RIGHTMOST_G_INDEX);
    Assertions.assertThat(GIndexUtil.gIdxLeftGIndex(0b10L)).isEqualTo(0b100L);
    Assertions.assertThat(GIndexUtil.gIdxRightGIndex(0b10L)).isEqualTo(0b101L);
    Assertions.assertThat(GIndexUtil.gIdxLeftGIndex(0b11L)).isEqualTo(0b110L);
    Assertions.assertThat(GIndexUtil.gIdxRightGIndex(0b11L)).isEqualTo(0b111L);
    assertThatThrownBy(() -> GIndexUtil.gIdxLeftGIndex(INVALID_G_INDEX));
    assertThatThrownBy(() -> GIndexUtil.gIdxLeftGIndex(GIndexUtil.LEFTMOST_G_INDEX));
    assertThatThrownBy(() -> GIndexUtil.gIdxLeftGIndex(GIndexUtil.RIGHTMOST_G_INDEX));
  }

  @Test
  void testChildGIndex() {
    Assertions.assertThat(GIndexUtil.gIdxChildGIndex(GIndexUtil.SELF_G_INDEX, 0, 0))
        .isEqualTo(GIndexUtil.SELF_G_INDEX);
    Assertions.assertThat(GIndexUtil.gIdxChildGIndex(GIndexUtil.LEFTMOST_G_INDEX, 0, 0))
        .isEqualTo(GIndexUtil.LEFTMOST_G_INDEX);
    Assertions.assertThat(GIndexUtil.gIdxChildGIndex(GIndexUtil.RIGHTMOST_G_INDEX, 0, 0))
        .isEqualTo(GIndexUtil.RIGHTMOST_G_INDEX);
    Assertions.assertThat(GIndexUtil.gIdxChildGIndex(GIndexUtil.SELF_G_INDEX, 0, 1))
        .isEqualTo(0b10L);
    Assertions.assertThat(GIndexUtil.gIdxChildGIndex(GIndexUtil.SELF_G_INDEX, 1, 1))
        .isEqualTo(0b11L);
    Assertions.assertThat(GIndexUtil.gIdxChildGIndex(GIndexUtil.SELF_G_INDEX, 0, 2))
        .isEqualTo(0b100L);
    Assertions.assertThat(GIndexUtil.gIdxChildGIndex(GIndexUtil.SELF_G_INDEX, 1, 2))
        .isEqualTo(0b101L);
    Assertions.assertThat(GIndexUtil.gIdxChildGIndex(GIndexUtil.SELF_G_INDEX, 2, 2))
        .isEqualTo(0b110L);
    Assertions.assertThat(GIndexUtil.gIdxChildGIndex(GIndexUtil.SELF_G_INDEX, 3, 2))
        .isEqualTo(0b111L);
    Assertions.assertThat(GIndexUtil.gIdxChildGIndex(0b100L, 0, 2)).isEqualTo(0b10000L);
    Assertions.assertThat(GIndexUtil.gIdxChildGIndex(0b100L, 1, 2)).isEqualTo(0b10001L);
    Assertions.assertThat(GIndexUtil.gIdxChildGIndex(0b100L, 2, 2)).isEqualTo(0b10010L);
    Assertions.assertThat(GIndexUtil.gIdxChildGIndex(0b100L, 3, 2)).isEqualTo(0b10011L);
    Assertions.assertThat(GIndexUtil.gIdxChildGIndex(GIndexUtil.SELF_G_INDEX, 0, 31))
        .isEqualTo(0b10000000_00000000_00000000_00000000L);
    Assertions.assertThat(GIndexUtil.gIdxChildGIndex(GIndexUtil.SELF_G_INDEX, 1, 31))
        .isEqualTo(0b10000000_00000000_00000000_00000001L);

    assertThatThrownBy(() -> GIndexUtil.gIdxChildGIndex(INVALID_G_INDEX, 0, 2));
    assertThatThrownBy(() -> GIndexUtil.gIdxChildGIndex(GIndexUtil.SELF_G_INDEX, 4, 2));
    assertThatThrownBy(() -> GIndexUtil.gIdxChildGIndex(GIndexUtil.LEFTMOST_G_INDEX, 0, 1));
    assertThatThrownBy(() -> GIndexUtil.gIdxChildGIndex(GIndexUtil.RIGHTMOST_G_INDEX, 0, 1));
    assertThatThrownBy(
        () ->
            GIndexUtil.gIdxChildGIndex(
                GIndexUtil.gIdxGetParent(GIndexUtil.LEFTMOST_G_INDEX), 0, 2));
  }

  @Test
  void testCombine() {
    Assertions.assertThat(GIndexUtil.gIdxCompose(GIndexUtil.SELF_G_INDEX, GIndexUtil.SELF_G_INDEX))
        .isEqualTo(GIndexUtil.SELF_G_INDEX);
    Assertions.assertThat(GIndexUtil.gIdxCompose(GIndexUtil.SELF_G_INDEX, 0b10101))
        .isEqualTo(0b10101);
    Assertions.assertThat(GIndexUtil.gIdxCompose(0b10101, GIndexUtil.SELF_G_INDEX))
        .isEqualTo(0b10101);
    Assertions.assertThat(GIndexUtil.gIdxCompose(0b10, 0b11)).isEqualTo(0b101);
    Assertions.assertThat(GIndexUtil.gIdxCompose(0b11, 0b11)).isEqualTo(0b111);
    Assertions.assertThat(GIndexUtil.gIdxCompose(0b10, 0b10)).isEqualTo(0b100);

    assertThatThrownBy(() -> GIndexUtil.gIdxCompose(GIndexUtil.LEFTMOST_G_INDEX, 0b11));
    assertThatThrownBy(() -> GIndexUtil.gIdxCompose(GIndexUtil.RIGHTMOST_G_INDEX, 0b11));
    assertThatThrownBy(() -> GIndexUtil.gIdxCompose(0xFFFFFFFF, 0x01FFFFFFFFL));
  }

  @Test
  void testLeftRightmost() {
    Assertions.assertThat(GIndexUtil.gIdxLeftmostFrom(GIndexUtil.SELF_G_INDEX))
        .isEqualTo(GIndexUtil.LEFTMOST_G_INDEX);
    Assertions.assertThat(GIndexUtil.gIdxRightmostFrom(GIndexUtil.SELF_G_INDEX))
        .isEqualTo(GIndexUtil.RIGHTMOST_G_INDEX);
    Assertions.assertThat(GIndexUtil.gIdxLeftmostFrom(0b10)).isEqualTo(GIndexUtil.LEFTMOST_G_INDEX);
    Assertions.assertThat(GIndexUtil.gIdxRightmostFrom(0b11))
        .isEqualTo(GIndexUtil.RIGHTMOST_G_INDEX);
    Assertions.assertThat(GIndexUtil.gIdxLeftmostFrom(0b11)).isEqualTo(0b11L << 62);
    Assertions.assertThat(GIndexUtil.gIdxRightmostFrom(0b10))
        .isEqualTo(0b1011111111111111_1111111111111111_1111111111111111_1111111111111111L);

    Assertions.assertThat(GIndexUtil.gIdxLeftmostFrom(GIndexUtil.LEFTMOST_G_INDEX))
        .isEqualTo(GIndexUtil.LEFTMOST_G_INDEX);
    Assertions.assertThat(GIndexUtil.gIdxRightmostFrom(GIndexUtil.LEFTMOST_G_INDEX))
        .isEqualTo(GIndexUtil.LEFTMOST_G_INDEX);
    Assertions.assertThat(GIndexUtil.gIdxLeftmostFrom(GIndexUtil.RIGHTMOST_G_INDEX))
        .isEqualTo(GIndexUtil.RIGHTMOST_G_INDEX);
    Assertions.assertThat(GIndexUtil.gIdxRightmostFrom(GIndexUtil.RIGHTMOST_G_INDEX))
        .isEqualTo(GIndexUtil.RIGHTMOST_G_INDEX);

    assertThatThrownBy(() -> GIndexUtil.gIdxLeftmostFrom(INVALID_G_INDEX));
    assertThatThrownBy(() -> GIndexUtil.gIdxRightmostFrom(INVALID_G_INDEX));
  }

  @Test
  void testGetChildIndex() {
    Assertions.assertThat(GIndexUtil.gIdxGetChildIndex(GIndexUtil.SELF_G_INDEX, 0)).isEqualTo(0);
    Assertions.assertThat(GIndexUtil.gIdxGetChildIndex(GIndexUtil.LEFTMOST_G_INDEX, 0))
        .isEqualTo(0);
    Assertions.assertThat(GIndexUtil.gIdxGetChildIndex(GIndexUtil.RIGHTMOST_G_INDEX, 0))
        .isEqualTo(0);
    Assertions.assertThat(GIndexUtil.gIdxGetChildIndex(GIndexUtil.LEFTMOST_G_INDEX, 22))
        .isEqualTo(0);
    Assertions.assertThat(
            GIndexUtil.gIdxGetChildIndex(GIndexUtil.LEFTMOST_G_INDEX, GIndexUtil.MAX_DEPTH))
        .isEqualTo(0);
    Assertions.assertThat(GIndexUtil.gIdxGetChildIndex(GIndexUtil.RIGHTMOST_G_INDEX, 1))
        .isEqualTo(1);
    Assertions.assertThat(GIndexUtil.gIdxGetChildIndex(GIndexUtil.RIGHTMOST_G_INDEX, 2))
        .isEqualTo(3);
    Assertions.assertThat(GIndexUtil.gIdxGetChildIndex(GIndexUtil.RIGHTMOST_G_INDEX, 3))
        .isEqualTo(7);
    Assertions.assertThat(GIndexUtil.gIdxGetChildIndex(GIndexUtil.RIGHTMOST_G_INDEX, 22))
        .isEqualTo((1 << 22) - 1);
    Assertions.assertThat(
            GIndexUtil.gIdxGetChildIndex(GIndexUtil.RIGHTMOST_G_INDEX, GIndexUtil.MAX_DEPTH))
        .isEqualTo(GIndexUtil.RIGHTMOST_G_INDEX);
    Assertions.assertThat(GIndexUtil.gIdxGetChildIndex(0b1100, 0)).isEqualTo(0);
    Assertions.assertThat(GIndexUtil.gIdxGetChildIndex(0b1100, 1)).isEqualTo(1);
    Assertions.assertThat(GIndexUtil.gIdxGetChildIndex(0b1100, 2)).isEqualTo(2);
    Assertions.assertThat(GIndexUtil.gIdxGetChildIndex(0b1100, 3)).isEqualTo(4);
    Assertions.assertThat(GIndexUtil.gIdxGetChildIndex(0b1000, 1)).isEqualTo(0);
    Assertions.assertThat(GIndexUtil.gIdxGetChildIndex(0b1000, 2)).isEqualTo(0);
    Assertions.assertThat(GIndexUtil.gIdxGetChildIndex(0b1000, 3)).isEqualTo(0);

    assertThatThrownBy(() -> GIndexUtil.gIdxGetChildIndex(INVALID_G_INDEX, 0));
    assertThatThrownBy(() -> GIndexUtil.gIdxGetChildIndex(INVALID_G_INDEX, 1));
    assertThatThrownBy(() -> GIndexUtil.gIdxGetChildIndex(GIndexUtil.SELF_G_INDEX, 1));
    assertThatThrownBy(() -> GIndexUtil.gIdxGetChildIndex(GIndexUtil.SELF_G_INDEX, 2));
    assertThatThrownBy(() -> GIndexUtil.gIdxGetChildIndex(GIndexUtil.SELF_G_INDEX, -1));
    assertThatThrownBy(() -> GIndexUtil.gIdxGetChildIndex(GIndexUtil.RIGHTMOST_G_INDEX, 64));
  }

  @Test
  void testRelativeGIndex() {
    Assertions.assertThat(GIndexUtil.gIdxGetRelativeGIndex(GIndexUtil.SELF_G_INDEX, 0))
        .isEqualTo(GIndexUtil.SELF_G_INDEX);
    Assertions.assertThat(GIndexUtil.gIdxGetRelativeGIndex(GIndexUtil.LEFTMOST_G_INDEX, 0))
        .isEqualTo(GIndexUtil.LEFTMOST_G_INDEX);
    Assertions.assertThat(GIndexUtil.gIdxGetRelativeGIndex(GIndexUtil.RIGHTMOST_G_INDEX, 0))
        .isEqualTo(GIndexUtil.RIGHTMOST_G_INDEX);
    Assertions.assertThat(GIndexUtil.gIdxGetRelativeGIndex(GIndexUtil.RIGHTMOST_G_INDEX, 62))
        .isEqualTo(0b11L);
    Assertions.assertThat(GIndexUtil.gIdxGetRelativeGIndex(0b100100111, 3)).isEqualTo(0b100111L);
    Assertions.assertThat(GIndexUtil.gIdxGetRelativeGIndex(0b10, 1))
        .isEqualTo(GIndexUtil.SELF_G_INDEX);
    Assertions.assertThat(GIndexUtil.gIdxGetRelativeGIndex(0b1011, 0)).isEqualTo(0b1011);
    Assertions.assertThat(GIndexUtil.gIdxGetRelativeGIndex(0b1011, 1)).isEqualTo(0b111);
    Assertions.assertThat(GIndexUtil.gIdxGetRelativeGIndex(0b1011, 2)).isEqualTo(0b11);
    Assertions.assertThat(GIndexUtil.gIdxGetRelativeGIndex(0b1011, 3))
        .isEqualTo(GIndexUtil.SELF_G_INDEX);

    assertThatThrownBy(() -> GIndexUtil.gIdxGetRelativeGIndex(GIndexUtil.SELF_G_INDEX, 1));
    assertThatThrownBy(() -> GIndexUtil.gIdxGetRelativeGIndex(0b10, 2));
    assertThatThrownBy(() -> GIndexUtil.gIdxGetRelativeGIndex(0b10, -1));
    assertThatThrownBy(() -> GIndexUtil.gIdxGetRelativeGIndex(0b10, 64));
  }
}
