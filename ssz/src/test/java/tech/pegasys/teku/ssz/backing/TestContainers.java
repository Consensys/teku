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
import tech.pegasys.teku.ssz.backing.containers.Container3;
import tech.pegasys.teku.ssz.backing.containers.ContainerType3;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.backing.type.SszContainerSchema;
import tech.pegasys.teku.ssz.backing.type.SszListSchema;
import tech.pegasys.teku.ssz.backing.type.SszVectorSchema;
import tech.pegasys.teku.ssz.backing.view.AbstractSszImmutableContainer;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.BitView;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.ByteView;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.Bytes32View;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.UInt64View;
import tech.pegasys.teku.ssz.backing.view.SszUtils;

public class TestContainers {

  public static class TestSubContainer extends AbstractSszImmutableContainer {

    public static final SszContainerSchema<TestSubContainer> TYPE =
        SszContainerSchema.create(
            List.of(SszPrimitiveSchemas.UINT64_TYPE, SszPrimitiveSchemas.BYTES32_TYPE),
            TestSubContainer::new);

    private TestSubContainer(SszContainerSchema<TestSubContainer> type, TreeNode backingNode) {
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

  public static class TestContainer extends AbstractSszImmutableContainer {

    public static final SszContainerSchema<TestContainer> TYPE =
        SszContainerSchema.create(
            List.of(TestSubContainer.TYPE, SszPrimitiveSchemas.UINT64_TYPE), TestContainer::new);

    private TestContainer(SszContainerSchema<TestContainer> type, TreeNode backingNode) {
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

  public static class TestSmallContainer extends AbstractSszImmutableContainer {

    public static final SszContainerSchema<TestSmallContainer> TYPE =
        SszContainerSchema.create(List.of(SszPrimitiveSchemas.BIT_TYPE), TestSmallContainer::new);

    private TestSmallContainer(SszContainerSchema<TestSmallContainer> type, TreeNode backingNode) {
      super(type, backingNode);
    }

    public TestSmallContainer(boolean val) {
      super(TYPE, BitView.viewOf(val));
    }
  }

  public static class TestByteVectorContainer extends AbstractSszImmutableContainer {

    public static final SszContainerSchema<TestByteVectorContainer> TYPE =
        SszContainerSchema.create(
            List.of(
                SszPrimitiveSchemas.UINT64_TYPE,
                new SszVectorSchema<ByteView>(SszPrimitiveSchemas.BYTE_TYPE, 64),
                SszPrimitiveSchemas.UINT64_TYPE),
            TestByteVectorContainer::new);

    public static TestByteVectorContainer random(Random random) {
      return new TestByteVectorContainer(
          random.nextLong(), Bytes.random(64, random), random.nextLong());
    }

    private TestByteVectorContainer(
        SszContainerSchema<TestByteVectorContainer> type, TreeNode backingNode) {
      super(type, backingNode);
    }

    public TestByteVectorContainer(long l1, Bytes b1, long l2) {
      super(
          TYPE,
          UInt64View.fromLong(l1),
          SszUtils.createVectorFromBytes(b1),
          UInt64View.fromLong(l2));
    }
  }

  public static class TestDoubleSuperContainer extends AbstractSszImmutableContainer {

    public static final SszContainerSchema<TestDoubleSuperContainer> TYPE =
        SszContainerSchema.create(
            List.of(
                SszPrimitiveSchemas.UINT64_TYPE,
                TestByteVectorContainer.TYPE,
                SszPrimitiveSchemas.UINT64_TYPE,
                TestByteVectorContainer.TYPE,
                SszPrimitiveSchemas.UINT64_TYPE),
            TestDoubleSuperContainer::new);

    private TestDoubleSuperContainer(
        SszContainerSchema<TestDoubleSuperContainer> type, TreeNode backingNode) {
      super(type, backingNode);
    }

    public TestDoubleSuperContainer(
        long l1, TestByteVectorContainer c1, long l2, TestByteVectorContainer c2, long l3) {
      super(
          TYPE, UInt64View.fromLong(l1), c1, UInt64View.fromLong(l2), c2, UInt64View.fromLong(l3));
    }
  }

  public static class VariableSizeContainer
      extends Container3<
          VariableSizeContainer, TestSubContainer, SszList<UInt64View>, UInt64View> {

    public static final ContainerType3<
            VariableSizeContainer, TestSubContainer, SszList<UInt64View>, UInt64View>
        TYPE =
            ContainerType3.create(
                TestSubContainer.TYPE,
                new SszListSchema<>(SszPrimitiveSchemas.UINT64_TYPE, 10),
                SszPrimitiveSchemas.UINT64_TYPE,
                VariableSizeContainer::new);

    private VariableSizeContainer(
        ContainerType3<
                VariableSizeContainer, TestSubContainer, SszList<UInt64View>, UInt64View>
            type,
        TreeNode backingNode) {
      super(type, backingNode);
    }
  }
}
