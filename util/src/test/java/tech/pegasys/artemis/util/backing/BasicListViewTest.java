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

import com.google.common.primitives.UnsignedLong;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.util.backing.tree.TreeNode;
import tech.pegasys.artemis.util.backing.type.BasicViewTypes;
import tech.pegasys.artemis.util.backing.type.ListViewType;
import tech.pegasys.artemis.util.backing.view.BasicViews.UInt64View;

public class BasicListViewTest {

  @Test
  public void simpleUInt64ListTest() {
    ListViewType<UInt64View> listType = new ListViewType<>(BasicViewTypes.UINT64_TYPE, 7);
    ListViewWrite<UInt64View> listView = listType.getDefault().createWritableCopy();
    TreeNode n0 = listView.getBackingNode();
    listView.append(new UInt64View(UnsignedLong.valueOf(0x111)));
    TreeNode n1 = listView.getBackingNode();
    listView.append(new UInt64View(UnsignedLong.valueOf(0x222)));
    listView.append(new UInt64View(UnsignedLong.valueOf(0x333)));
    listView.append(new UInt64View(UnsignedLong.valueOf(0x444)));
    TreeNode n2 = listView.getBackingNode();
    listView.append(new UInt64View(UnsignedLong.valueOf(0x555)));
    TreeNode n3 = listView.getBackingNode();
    listView.append(new UInt64View(UnsignedLong.valueOf(0x666)));
    listView.append(new UInt64View(UnsignedLong.valueOf(0x777)));
    TreeNode n4 = listView.getBackingNode();
    listView.set(0, new UInt64View(UnsignedLong.valueOf(0x800)));
    TreeNode n5 = listView.getBackingNode();
    listView.set(1, new UInt64View(UnsignedLong.valueOf(0x801)));
    listView.set(2, new UInt64View(UnsignedLong.valueOf(0x802)));
    listView.set(3, new UInt64View(UnsignedLong.valueOf(0x803)));
    listView.set(4, new UInt64View(UnsignedLong.valueOf(0x804)));
    TreeNode n6 = listView.getBackingNode();
    System.out.println(n0);
    System.out.println(n1);
    System.out.println(n2);
    System.out.println(n3);
    System.out.println(n4);
    System.out.println(n5);
    System.out.println(n6);

    Assertions.assertEquals(0, listType.createFromBackingNode(n0).size());
    Assertions.assertEquals(1, listType.createFromBackingNode(n1).size());
    Assertions.assertEquals(0x111, listType.createFromBackingNode(n1).get(0).longValue());
    Assertions.assertEquals(4, listType.createFromBackingNode(n2).size());
    Assertions.assertEquals(0x111, listType.createFromBackingNode(n2).get(0).longValue());
    Assertions.assertEquals(0x222, listType.createFromBackingNode(n2).get(1).longValue());
    Assertions.assertEquals(0x333, listType.createFromBackingNode(n2).get(2).longValue());
    Assertions.assertEquals(0x444, listType.createFromBackingNode(n2).get(3).longValue());
    Assertions.assertEquals(5, listType.createFromBackingNode(n3).size());
    Assertions.assertEquals(0x111, listType.createFromBackingNode(n3).get(0).longValue());
    Assertions.assertEquals(0x222, listType.createFromBackingNode(n3).get(1).longValue());
    Assertions.assertEquals(0x333, listType.createFromBackingNode(n3).get(2).longValue());
    Assertions.assertEquals(0x444, listType.createFromBackingNode(n3).get(3).longValue());
    Assertions.assertEquals(0x555, listType.createFromBackingNode(n3).get(4).longValue());
    Assertions.assertEquals(7, listType.createFromBackingNode(n4).size());
    Assertions.assertEquals(0x666, listType.createFromBackingNode(n4).get(5).longValue());
    Assertions.assertEquals(0x777, listType.createFromBackingNode(n4).get(6).longValue());
    Assertions.assertEquals(0x800, listType.createFromBackingNode(n5).get(0).longValue());
    Assertions.assertEquals(0x222, listType.createFromBackingNode(n5).get(1).longValue());

    Assertions.assertThrows(
        IndexOutOfBoundsException.class,
        () ->
            listType
                .createFromBackingNode(n3)
                .createWritableCopy()
                .set(7, new UInt64View(UnsignedLong.valueOf(0xaaa))));
    Assertions.assertThrows(
        IndexOutOfBoundsException.class, () -> listType.createFromBackingNode(n3).get(7));
    Assertions.assertThrows(
        IndexOutOfBoundsException.class, () -> listType.createFromBackingNode(n3).get(8));
    Assertions.assertThrows(
        IndexOutOfBoundsException.class,
        () -> listView.set(7, new UInt64View(UnsignedLong.valueOf(0xaaa))));
    Assertions.assertThrows(
        IndexOutOfBoundsException.class,
        () -> listView.append(new UInt64View(UnsignedLong.valueOf(0xaaa))));

    listView.clear();
    Assertions.assertEquals(n0.hashTreeRoot(), listView.hashTreeRoot());
  }
}
