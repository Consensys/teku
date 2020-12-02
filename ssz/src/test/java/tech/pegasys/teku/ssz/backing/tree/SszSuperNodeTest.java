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

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.TestUtil.TestContainer;
import tech.pegasys.teku.ssz.TestUtil.TestSubContainer;
import tech.pegasys.teku.ssz.backing.ListViewRead;
import tech.pegasys.teku.ssz.backing.ListViewWrite;
import tech.pegasys.teku.ssz.backing.type.ListViewType;
import tech.pegasys.teku.ssz.backing.type.TypeHints;
import tech.pegasys.teku.ssz.backing.type.ViewType;

public class SszSuperNodeTest {

  ViewType listElementType = TestContainer.TYPE;

  ListViewType<TestContainer> listTypeSuperNode =
      new ListViewType<>(listElementType, 10, TypeHints.sszSuperNode(2));
  ListViewType<TestContainer> listTypeSimple = new ListViewType<>(listElementType, 10);

  ListViewRead<TestContainer> lr1_1 = listTypeSuperNode.getDefault();
  ListViewRead<TestContainer> lr2_1 = listTypeSimple.getDefault();

  Bytes32 bytes32 =
      Bytes32.fromHexString("0x2222222222222222222222222222222222222222222222222222222222222222");
  TestSubContainer subContainer = new TestSubContainer(UInt64.valueOf(0x111111), bytes32);
  TestContainer testContainer = new TestContainer(subContainer, UInt64.valueOf(0x333333));

  @Test
  void test1() {

    assertThat(listTypeSuperNode.getDefaultTree().hashTreeRoot())
        .isEqualTo(listTypeSimple.getDefaultTree().hashTreeRoot());

    ListViewWrite<TestContainer> lw1_1 = lr1_1.createWritableCopy();
    ListViewWrite<TestContainer> lw2_1 = lr2_1.createWritableCopy();

    lw1_1.append(testContainer);
    lw2_1.append(testContainer);

    ListViewRead<TestContainer> lr1_2 = lw1_1.commitChanges();
    ListViewRead<TestContainer> lr2_2 = lw2_1.commitChanges();

    assertThat(lr1_2.hashTreeRoot()).isEqualTo(lr2_2.hashTreeRoot());
  }
}
