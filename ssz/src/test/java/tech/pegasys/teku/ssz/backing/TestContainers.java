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
import tech.pegasys.teku.ssz.backing.collections.SszByteVector;
import tech.pegasys.teku.ssz.backing.containers.Container3;
import tech.pegasys.teku.ssz.backing.containers.ContainerSchema3;
import tech.pegasys.teku.ssz.backing.schema.SszContainerSchema;
import tech.pegasys.teku.ssz.backing.schema.SszListSchema;
import tech.pegasys.teku.ssz.backing.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.backing.schema.SszVectorSchema;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.view.AbstractSszImmutableContainer;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszBit;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszBytes32;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszUInt64;

public class TestContainers {

  public static class TestSubContainer extends AbstractSszImmutableContainer {

    public static final SszContainerSchema<TestSubContainer> SSZ_SCHEMA =
        SszContainerSchema.create(
            List.of(SszPrimitiveSchemas.UINT64_SCHEMA, SszPrimitiveSchemas.BYTES32_SCHEMA),
            TestSubContainer::new);

    private TestSubContainer(SszContainerSchema<TestSubContainer> type, TreeNode backingNode) {
      super(type, backingNode);
    }

    public TestSubContainer(UInt64 long1, Bytes32 bytes1) {
      super(SSZ_SCHEMA, SszUInt64.of(long1), SszBytes32.of(bytes1));
    }

    public UInt64 getLong1() {
      return ((SszUInt64) get(0)).get();
    }

    public Bytes32 getBytes1() {
      return ((SszBytes32) get(1)).get();
    }
  }

  public static class TestContainer extends AbstractSszImmutableContainer {

    public static final SszContainerSchema<TestContainer> SSZ_SCHEMA =
        SszContainerSchema.create(
            List.of(TestSubContainer.SSZ_SCHEMA, SszPrimitiveSchemas.UINT64_SCHEMA),
            TestContainer::new);

    private TestContainer(SszContainerSchema<TestContainer> type, TreeNode backingNode) {
      super(type, backingNode);
    }

    public TestContainer(TestSubContainer subContainer, UInt64 long1) {
      super(SSZ_SCHEMA, subContainer, SszUInt64.of(long1));
    }

    public TestSubContainer getSubContainer() {
      return (TestSubContainer) get(0);
    }

    public UInt64 getLong() {
      return ((SszUInt64) get(1)).get();
    }
  }

  public static class TestSmallContainer extends AbstractSszImmutableContainer {

    public static final SszContainerSchema<TestSmallContainer> SSZ_SCHEMA =
        SszContainerSchema.create(List.of(SszPrimitiveSchemas.BIT_SCHEMA), TestSmallContainer::new);

    private TestSmallContainer(SszContainerSchema<TestSmallContainer> type, TreeNode backingNode) {
      super(type, backingNode);
    }

    public TestSmallContainer(boolean val) {
      super(SSZ_SCHEMA, SszBit.of(val));
    }
  }

  public static class TestByteVectorContainer extends AbstractSszImmutableContainer {

    public static final SszContainerSchema<TestByteVectorContainer> SSZ_SCHEMA =
        SszContainerSchema.create(
            List.of(
                SszPrimitiveSchemas.UINT64_SCHEMA,
                SszVectorSchema.create(SszPrimitiveSchemas.BYTE_SCHEMA, 64),
                SszPrimitiveSchemas.UINT64_SCHEMA),
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
          SSZ_SCHEMA,
          SszUInt64.of(UInt64.fromLongBits(l1)),
          SszByteVector.fromBytes(b1),
          SszUInt64.of(UInt64.fromLongBits(l2)));
    }
  }

  public static class TestDoubleSuperContainer extends AbstractSszImmutableContainer {

    public static final SszContainerSchema<TestDoubleSuperContainer> SSZ_SCHEMA =
        SszContainerSchema.create(
            List.of(
                SszPrimitiveSchemas.UINT64_SCHEMA,
                TestByteVectorContainer.SSZ_SCHEMA,
                SszPrimitiveSchemas.UINT64_SCHEMA,
                TestByteVectorContainer.SSZ_SCHEMA,
                SszPrimitiveSchemas.UINT64_SCHEMA),
            TestDoubleSuperContainer::new);

    private TestDoubleSuperContainer(
        SszContainerSchema<TestDoubleSuperContainer> type, TreeNode backingNode) {
      super(type, backingNode);
    }

    public TestDoubleSuperContainer(
        long l1, TestByteVectorContainer c1, long l2, TestByteVectorContainer c2, long l3) {
      super(
          SSZ_SCHEMA,
          SszUInt64.of(UInt64.fromLongBits(l1)),
          c1,
          SszUInt64.of(UInt64.fromLongBits(l2)),
          c2,
          SszUInt64.of(UInt64.fromLongBits(l3)));
    }
  }

  public static class VariableSizeContainer
      extends Container3<VariableSizeContainer, TestSubContainer, SszList<SszUInt64>, SszUInt64> {

    public static final ContainerSchema3<
            VariableSizeContainer, TestSubContainer, SszList<SszUInt64>, SszUInt64>
        SSZ_SCHEMA =
            ContainerSchema3.create(
                TestSubContainer.SSZ_SCHEMA,
                SszListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 10),
                SszPrimitiveSchemas.UINT64_SCHEMA,
                VariableSizeContainer::new);

    private VariableSizeContainer(
        ContainerSchema3<VariableSizeContainer, TestSubContainer, SszList<SszUInt64>, SszUInt64>
            type,
        TreeNode backingNode) {
      super(type, backingNode);
    }
  }
}
