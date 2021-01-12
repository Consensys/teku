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

import java.util.List;
import java.util.Random;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.BasicViewTypes;
import tech.pegasys.teku.ssz.backing.type.ContainerViewType;
import tech.pegasys.teku.ssz.backing.type.VectorViewType;
import tech.pegasys.teku.ssz.backing.view.AbstractImmutableContainer;
import tech.pegasys.teku.ssz.backing.view.BasicViews.BitView;
import tech.pegasys.teku.ssz.backing.view.BasicViews.ByteView;
import tech.pegasys.teku.ssz.backing.view.BasicViews.Bytes32View;
import tech.pegasys.teku.ssz.backing.view.BasicViews.UInt64View;
import tech.pegasys.teku.ssz.backing.view.ViewUtils;
import tech.pegasys.teku.ssz.sos.SszTypeDescriptor;

public class TestContainers {

  public static class TestSubContainer extends AbstractImmutableContainer {

    @SszTypeDescriptor
    public static final ContainerViewType<TestSubContainer> TYPE =
        ContainerViewType.create(
            List.of(BasicViewTypes.UINT64_TYPE, BasicViewTypes.BYTES32_TYPE),
            TestSubContainer::new);

    private TestSubContainer(ContainerViewType<TestSubContainer> type, TreeNode backingNode) {
      super(type, backingNode);
    }

    public TestSubContainer(UInt64 long1, Bytes32 bytes1) {
      super(TYPE, new UInt64View(long1), new Bytes32View(bytes1));
    }

    public UInt64 getLong1() {
      return ((UInt64View) get(0)).get();
    }

    public Bytes32 getBytes1() {
      return ((Bytes32View) get(1)).get();
    }
  }

  public static class TestContainer extends AbstractImmutableContainer {

    @SszTypeDescriptor
    public static final ContainerViewType<TestContainer> TYPE =
        ContainerViewType.create(
            List.of(TestSubContainer.TYPE, BasicViewTypes.UINT64_TYPE), TestContainer::new);

    private TestContainer(ContainerViewType<TestContainer> type, TreeNode backingNode) {
      super(type, backingNode);
    }

    public TestContainer(TestSubContainer subContainer, UInt64 long1) {
      super(TYPE, subContainer, new UInt64View(long1));
    }

    public TestSubContainer getSubContainer() {
      return (TestSubContainer) get(0);
    }

    public UInt64 getLong() {
      return ((UInt64View) get(1)).get();
    }
  }

  public static class TestSmallContainer extends AbstractImmutableContainer {

    @SszTypeDescriptor
    public static final ContainerViewType<TestSmallContainer> TYPE =
        ContainerViewType.create(List.of(BasicViewTypes.BIT_TYPE), TestSmallContainer::new);

    private TestSmallContainer(ContainerViewType<TestSmallContainer> type, TreeNode backingNode) {
      super(type, backingNode);
    }

    public TestSmallContainer(boolean val) {
      super(TYPE, BitView.viewOf(val));
    }
  }

  public static class TestByteVectorContainer extends AbstractImmutableContainer {

    @SszTypeDescriptor
    public static final ContainerViewType<TestByteVectorContainer> TYPE =
        ContainerViewType.create(
            List.of(
                BasicViewTypes.UINT64_TYPE,
                new VectorViewType<ByteView>(BasicViewTypes.BYTE_TYPE, 64),
                BasicViewTypes.UINT64_TYPE),
            TestByteVectorContainer::new);

    public static TestByteVectorContainer random(Random random) {
      return new TestByteVectorContainer(
          random.nextLong(), Bytes.random(64, random), random.nextLong());
    }

    private TestByteVectorContainer(
        ContainerViewType<TestByteVectorContainer> type, TreeNode backingNode) {
      super(type, backingNode);
    }

    public TestByteVectorContainer(long l1, Bytes b1, long l2) {
      super(
          TYPE,
          UInt64View.fromLong(l1),
          ViewUtils.createVectorFromBytes(b1),
          UInt64View.fromLong(l2));
    }
  }

  public static class TestDoubleSuperContainer extends AbstractImmutableContainer {

    @SszTypeDescriptor
    public static final ContainerViewType<TestDoubleSuperContainer> TYPE =
        ContainerViewType.create(
            List.of(
                BasicViewTypes.UINT64_TYPE,
                TestByteVectorContainer.TYPE,
                BasicViewTypes.UINT64_TYPE,
                TestByteVectorContainer.TYPE,
                BasicViewTypes.UINT64_TYPE),
            TestDoubleSuperContainer::new);

    private TestDoubleSuperContainer(
        ContainerViewType<TestDoubleSuperContainer> type, TreeNode backingNode) {
      super(type, backingNode);
    }

    public TestDoubleSuperContainer(
        long l1, TestByteVectorContainer c1, long l2, TestByteVectorContainer c2, long l3) {
      super(
          TYPE, UInt64View.fromLong(l1), c1, UInt64View.fromLong(l2), c2, UInt64View.fromLong(l3));
    }
  }
}
