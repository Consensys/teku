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

import static org.apache.tuweni.bytes.Bytes.concatenate;
import static org.apache.tuweni.bytes.Bytes32.ZERO;
import static org.apache.tuweni.crypto.Hash.sha2_256;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ssz.backing.tree.LeafNode;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.ListViewType;
import tech.pegasys.teku.ssz.backing.type.ViewType;
import tech.pegasys.teku.ssz.sos.SszReader;

public class CompositeListViewTest {

  static ViewType<?> testType =
      new ViewType<>() {

        @Override
        public TreeNode getDefaultTree() {
          throw new UnsupportedOperationException();
        }

        @Override
        public TestView getDefault() {
          return new TestView(0);
        }

        @Override
        public TestView createFromBackingNode(TreeNode node) {
          return new TestView(node);
        }

        @Override
        public int getBitsSize() {
          return 256;
        }

        @Override
        public boolean isFixedSize() {
          return true;
        }

        @Override
        public int getFixedPartSize() {
          return 0;
        }

        @Override
        public int getVariablePartSize(TreeNode node) {
          return 0;
        }

        @Override
        public int sszSerialize(TreeNode node, Consumer<Bytes> writer) {
          return 0;
        }

        @Override
        public TreeNode sszDeserializeTree(SszReader reader) {
          return null;
        }
      };

  static class TestView implements ViewRead {
    TreeNode node;
    public final int v;

    public TestView(int v) {
      this.v = v;
    }

    public TestView(TreeNode node) {
      this.node = node;
      this.v = node.hashTreeRoot().trimLeadingZeros().toInt();
    }

    @Override
    public ViewType<?> getType() {
      return testType;
    }

    @Override
    public TreeNode getBackingNode() {
      if (node == null) {
        node = LeafNode.create(Bytes32.leftPad(Bytes.ofUnsignedInt(v)));
      }
      return node;
    }

    @Override
    public ViewWrite createWritableCopy() {
      throw new UnsupportedOperationException();
    }
  }

  @Test
  public void simpleTest1() {
    ListViewType<TestView> listType = new ListViewType<>(testType, 3);
    ListViewWrite<TestView> list = listType.getDefault().createWritableCopy();
    TreeNode n0 = list.commitChanges().getBackingNode();
    list.set(0, new TestView(0x111));
    TreeNode n1 = list.commitChanges().getBackingNode();
    list.set(1, new TestView(0x222));
    TreeNode n2 = list.commitChanges().getBackingNode();
    list.set(2, new TestView(0x333));
    TreeNode n3 = list.commitChanges().getBackingNode();
    list.set(0, new TestView(0x444));
    TreeNode n4 = list.commitChanges().getBackingNode();

    assertThat(listType.createFromBackingNode(n0).size()).isEqualTo(0);
    assertThat(listType.createFromBackingNode(n1).size()).isEqualTo(1);
    assertThat(listType.createFromBackingNode(n1).get(0).v).isEqualTo(0x111);
    assertThat(listType.createFromBackingNode(n2).size()).isEqualTo(2);
    assertThat(listType.createFromBackingNode(n2).get(0).v).isEqualTo(0x111);
    assertThat(listType.createFromBackingNode(n2).get(1).v).isEqualTo(0x222);
    assertThat(listType.createFromBackingNode(n3).size()).isEqualTo(3);
    assertThat(listType.createFromBackingNode(n3).get(0).v).isEqualTo(0x111);
    assertThat(listType.createFromBackingNode(n3).get(1).v).isEqualTo(0x222);
    assertThat(listType.createFromBackingNode(n3).get(2).v).isEqualTo(0x333);
    assertThat(listType.createFromBackingNode(n4).size()).isEqualTo(3);
    assertThat(listType.createFromBackingNode(n4).get(0).v).isEqualTo(0x444);
    assertThat(listType.createFromBackingNode(n4).get(1).v).isEqualTo(0x222);
    assertThat(listType.createFromBackingNode(n4).get(2).v).isEqualTo(0x333);

    assertThat(n0.hashTreeRoot())
        .isEqualTo(
            sha2_256(
                concatenate(
                    sha2_256(
                        concatenate(
                            sha2_256(concatenate(ZERO, ZERO)), sha2_256(concatenate(ZERO, ZERO)))),
                    ZERO)));

    assertThat(n1.hashTreeRoot())
        .isEqualTo(
            sha2_256(
                concatenate(
                    sha2_256(
                        concatenate(
                            sha2_256(
                                concatenate(
                                    Bytes32.fromHexString(
                                        "0x0000000000000000000000000000000000000000000000000000000000000111"),
                                    ZERO)),
                            sha2_256(concatenate(ZERO, ZERO)))),
                    Bytes32.fromHexString(
                        "0x0100000000000000000000000000000000000000000000000000000000000000"))));

    assertThat(n2.hashTreeRoot())
        .isEqualTo(
            sha2_256(
                concatenate(
                    sha2_256(
                        concatenate(
                            sha2_256(
                                concatenate(
                                    Bytes32.fromHexString(
                                        "0x0000000000000000000000000000000000000000000000000000000000000111"),
                                    Bytes32.fromHexString(
                                        "0x0000000000000000000000000000000000000000000000000000000000000222"))),
                            sha2_256(concatenate(ZERO, ZERO)))),
                    Bytes32.fromHexString(
                        "0x0200000000000000000000000000000000000000000000000000000000000000"))));

    assertThat(n3.hashTreeRoot())
        .isEqualTo(
            sha2_256(
                concatenate(
                    sha2_256(
                        concatenate(
                            sha2_256(
                                concatenate(
                                    Bytes32.fromHexString(
                                        "0x0000000000000000000000000000000000000000000000000000000000000111"),
                                    Bytes32.fromHexString(
                                        "0x0000000000000000000000000000000000000000000000000000000000000222"))),
                            sha2_256(
                                concatenate(
                                    Bytes32.fromHexString(
                                        "0x0000000000000000000000000000000000000000000000000000000000000333"),
                                    ZERO)))),
                    Bytes32.fromHexString(
                        "0x0300000000000000000000000000000000000000000000000000000000000000"))));
  }
}
