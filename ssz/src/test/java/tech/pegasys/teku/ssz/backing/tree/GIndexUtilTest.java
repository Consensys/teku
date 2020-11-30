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
import static tech.pegasys.teku.ssz.backing.tree.GIndexUtil.NodeRelation.Left;
import static tech.pegasys.teku.ssz.backing.tree.GIndexUtil.NodeRelation.Predecessor;
import static tech.pegasys.teku.ssz.backing.tree.GIndexUtil.NodeRelation.Right;
import static tech.pegasys.teku.ssz.backing.tree.GIndexUtil.NodeRelation.Same;
import static tech.pegasys.teku.ssz.backing.tree.GIndexUtil.gIdxCompare;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.ssz.backing.tree.GIndexUtil.NodeRelation;

public class GIndexUtilTest {


  static Stream<Arguments> compareCases() {
    return Stream.of(
        Arguments.of(0b1L, 0b1L, Same),
        Arguments.of(0b1L, 0b11L, Predecessor),
        Arguments.of(0b1L, 0b10L, Predecessor),
        Arguments.of(0b10L, 0b11L, Left),
        Arguments.of(0b11L, 0b10L, Right)
    );
  }

  @ParameterizedTest
  @MethodSource("compareCases")
  void testCompare(long idx1, long idx2, NodeRelation expected) {
    assertThat(gIdxCompare(idx1, idx2)).isEqualTo(expected);
  }
}
