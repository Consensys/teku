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

package tech.pegasys.teku.ssz.backing;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.backing.TestContainers.TestSubContainer;
import tech.pegasys.teku.ssz.backing.type.ListViewType;

public class ListViewTest {

  @Test
  void clearTest() {
    ListViewType<TestSubContainer> type =
        new ListViewType<>(TestContainers.TestSubContainer.TYPE, 100);
    ListViewRead<TestSubContainer> lr1 = type.getDefault();
    ListViewWrite<TestSubContainer> lw1 = lr1.createWritableCopy();
    lw1.append(new TestSubContainer(UInt64.valueOf(0x111), Bytes32.leftPad(Bytes.of(0x22))));
    lw1.append(new TestSubContainer(UInt64.valueOf(0x111), Bytes32.leftPad(Bytes.of(0x22))));
    ListViewWrite<TestSubContainer> lw2 = lw1.commitChanges().createWritableCopy();
    lw2.clear();
    ListViewRead<TestSubContainer> lr2 = lw2.commitChanges();
    assertThat(lr1.hashTreeRoot()).isEqualTo(lr2.hashTreeRoot());

    ListViewWrite<TestSubContainer> lw3 = lw1.commitChanges().createWritableCopy();
    lw3.clear();
    lw3.append(new TestSubContainer(UInt64.valueOf(0x111), Bytes32.leftPad(Bytes.of(0x22))));
    ListViewRead<TestSubContainer> lr3 = lw3.commitChanges();
    assertThat(lr3.size()).isEqualTo(1);
  }
}
