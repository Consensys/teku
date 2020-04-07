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

package tech.pegasys.artemis.util.backing;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.ssz.backing.ListViewRead;
import tech.pegasys.artemis.ssz.backing.ListViewWrite;
import tech.pegasys.artemis.ssz.backing.tree.TreeNode;
import tech.pegasys.artemis.ssz.backing.type.BasicViewTypes;
import tech.pegasys.artemis.ssz.backing.type.ContainerViewType;
import tech.pegasys.artemis.ssz.backing.type.ListViewType;
import tech.pegasys.artemis.ssz.backing.view.AbstractImmutableContainer;
import tech.pegasys.artemis.ssz.backing.view.BasicViews.Bytes32View;
import tech.pegasys.artemis.ssz.backing.view.BasicViews.UInt64View;

public class ListViewTest {

  public static class SubContainer extends AbstractImmutableContainer {

    public static final ContainerViewType<SubContainer> TYPE =
        new ContainerViewType<>(
            List.of(BasicViewTypes.UINT64_TYPE, BasicViewTypes.BYTES32_TYPE), SubContainer::new);

    private SubContainer(ContainerViewType<SubContainer> type, TreeNode backingNode) {
      super(type, backingNode);
    }

    public SubContainer(UnsignedLong long1, Bytes32 bytes1) {
      super(TYPE, new UInt64View(long1), new Bytes32View(bytes1));
    }

    public UnsignedLong getLong1() {
      return ((UInt64View) get(0)).get();
    }

    public Bytes32 getBytes1() {
      return ((Bytes32View) get(1)).get();
    }
  }

  @Test
  void clearTest() {
    ListViewType<SubContainer> type = new ListViewType<>(SubContainer.TYPE, 100);
    ListViewRead<SubContainer> lr1 = type.getDefault();
    ListViewWrite<SubContainer> lw1 = lr1.createWritableCopy();
    lw1.append(new SubContainer(UnsignedLong.valueOf(0x111), Bytes32.leftPad(Bytes.of(0x22))));
    lw1.append(new SubContainer(UnsignedLong.valueOf(0x111), Bytes32.leftPad(Bytes.of(0x22))));
    ListViewWrite<SubContainer> lw2 = lw1.commitChanges().createWritableCopy();
    lw2.clear();
    ListViewRead<SubContainer> lr2 = lw2.commitChanges();
    assertThat(lr1.hashTreeRoot()).isEqualTo(lr2.hashTreeRoot());

    ListViewWrite<SubContainer> lw3 = lw1.commitChanges().createWritableCopy();
    lw3.clear();
    lw3.append(new SubContainer(UnsignedLong.valueOf(0x111), Bytes32.leftPad(Bytes.of(0x22))));
    ListViewRead<SubContainer> lr3 = lw3.commitChanges();
    assertThat(lr3.size()).isEqualTo(1);
  }
}
