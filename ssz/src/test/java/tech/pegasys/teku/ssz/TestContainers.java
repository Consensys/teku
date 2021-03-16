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

package tech.pegasys.teku.ssz;

import java.util.List;
import java.util.Random;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.cache.IntCache;
import tech.pegasys.teku.ssz.collections.SszByteVector;
import tech.pegasys.teku.ssz.containers.Container3;
import tech.pegasys.teku.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.ssz.impl.AbstractSszImmutableContainer;
import tech.pegasys.teku.ssz.impl.SszContainerImpl;
import tech.pegasys.teku.ssz.impl.SszMutableContainerImpl;
import tech.pegasys.teku.ssz.impl.SszPrimitives.SszBit;
import tech.pegasys.teku.ssz.impl.SszPrimitives.SszBytes32;
import tech.pegasys.teku.ssz.impl.SszPrimitives.SszUInt64;
import tech.pegasys.teku.ssz.schema.SszCompositeSchema;
import tech.pegasys.teku.ssz.schema.SszContainerSchema;
import tech.pegasys.teku.ssz.schema.SszListSchema;
import tech.pegasys.teku.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.schema.SszVectorSchema;
import tech.pegasys.teku.ssz.schema.impl.AbstractSszContainerSchema.NamedSchema;
import tech.pegasys.teku.ssz.tree.TreeNode;

public class TestContainers {

  public interface ImmutableSubContainer extends SszContainer {

    UInt64 getLong1();

    Bytes32 getBytes1();
  }

  public interface WritableSubContainer extends SszContainer {

    SszContainerSchema<WritableSubContainer> SSZ_SCHEMA =
        SszContainerSchema.create(
            "WritableSubContainer",
            List.of(
                NamedSchema.of("long1", SszPrimitiveSchemas.UINT64_SCHEMA),
                NamedSchema.of("long2", SszPrimitiveSchemas.UINT64_SCHEMA)),
            SubContainerReadImpl::new);

    default UInt64 getLong1() {
      return ((SszUInt64) get(0)).get();
    }

    default UInt64 getLong2() {
      return ((SszUInt64) get(1)).get();
    }
  }

  public interface WritableMutableSubContainer
      extends WritableSubContainer, SszMutableRefContainer {

    default void setLong1(UInt64 val) {
      set(0, SszUInt64.of(val));
    }

    default void setLong2(UInt64 val) {
      set(1, SszUInt64.of(val));
    }
  }

  public interface WritableContainer extends SszContainer {

    SszContainerSchema<ContainerReadImpl> SSZ_SCHEMA =
        SszContainerSchema.create(
            "WritableContainer",
            List.of(
                NamedSchema.of("long1", SszPrimitiveSchemas.UINT64_SCHEMA),
                NamedSchema.of("long2", SszPrimitiveSchemas.UINT64_SCHEMA),
                NamedSchema.of("sub1", WritableSubContainer.SSZ_SCHEMA),
                NamedSchema.of(
                    "list1", SszListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 10)),
                NamedSchema.of("list2", SszListSchema.create(WritableSubContainer.SSZ_SCHEMA, 2)),
                NamedSchema.of(
                    "vector1", SszVectorSchema.create(ImmutableSubContainerImpl.SSZ_SCHEMA, 2))),
            ContainerReadImpl::new);

    static WritableContainer createDefault() {
      return ContainerReadImpl.SSZ_SCHEMA.getDefault();
    }

    default UInt64 getLong1() {
      return ((SszUInt64) get(0)).get();
    }

    default UInt64 getLong2() {
      return ((SszUInt64) get(1)).get();
    }

    default WritableSubContainer getSub1() {
      return (WritableSubContainer) get(2);
    }

    default SszList<SszUInt64> getList1() {
      return getAny(3);
    }

    default SszList<WritableSubContainer> getList2() {
      return getAny(4);
    }

    default SszVector<ImmutableSubContainer> getVector1() {
      return getAny(5);
    }

    @Override
    WritableMutableContainer createWritableCopy();
  }

  public interface WritableMutableContainer extends WritableContainer, SszMutableRefContainer {

    void setLong1(UInt64 val);

    void setLong2(UInt64 val);

    @Override
    WritableMutableSubContainer getSub1();

    @Override
    SszMutableList<SszUInt64> getList1();

    @Override
    SszMutableRefList<WritableSubContainer, WritableMutableSubContainer> getList2();

    @Override
    SszMutableVector<ImmutableSubContainer> getVector1();

    @Override
    WritableContainer commitChanges();
  }

  public static class TestSubContainer extends AbstractSszImmutableContainer {

    public static final SszContainerSchema<TestSubContainer> SSZ_SCHEMA =
        SszContainerSchema.create(
            "TestSubContainer",
            List.of(
                NamedSchema.of("long1", SszPrimitiveSchemas.UINT64_SCHEMA),
                NamedSchema.of("bytes1", SszPrimitiveSchemas.BYTES32_SCHEMA)),
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
            "TestContainer",
            List.of(
                NamedSchema.of("subContainer", TestSubContainer.SSZ_SCHEMA),
                NamedSchema.of("long", SszPrimitiveSchemas.UINT64_SCHEMA)),
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
        SszContainerSchema.create(
            "TestSmallContainer",
            List.of(NamedSchema.of("bit", SszPrimitiveSchemas.BIT_SCHEMA)),
            TestSmallContainer::new);

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
            "TestByteVectorContainer",
            List.of(
                NamedSchema.of("long1", SszPrimitiveSchemas.UINT64_SCHEMA),
                NamedSchema.of(
                    "bytevector", SszVectorSchema.create(SszPrimitiveSchemas.BYTE_SCHEMA, 64)),
                NamedSchema.of("long2", SszPrimitiveSchemas.UINT64_SCHEMA)),
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
            "TestDoubleSuperContainer",
            List.of(
                NamedSchema.of("long1", SszPrimitiveSchemas.UINT64_SCHEMA),
                NamedSchema.of("subContainer1", TestByteVectorContainer.SSZ_SCHEMA),
                NamedSchema.of("long2", SszPrimitiveSchemas.UINT64_SCHEMA),
                NamedSchema.of("subContainer2", TestByteVectorContainer.SSZ_SCHEMA),
                NamedSchema.of("long3", SszPrimitiveSchemas.UINT64_SCHEMA)),
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
            new ContainerSchema3<>(
                "VariableSizeContainer",
                NamedSchema.of("sub", TestSubContainer.SSZ_SCHEMA),
                NamedSchema.of("list", SszListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 10)),
                NamedSchema.of("long", SszPrimitiveSchemas.UINT64_SCHEMA)) {
              @Override
              public VariableSizeContainer createFromBackingNode(TreeNode node) {
                return new VariableSizeContainer(this, node);
              }
            };

    private VariableSizeContainer(
        ContainerSchema3<VariableSizeContainer, TestSubContainer, SszList<SszUInt64>, SszUInt64>
            type,
        TreeNode backingNode) {
      super(type, backingNode);
    }
  }

  public static class ImmutableSubContainerImpl extends AbstractSszImmutableContainer
      implements ImmutableSubContainer {

    public static final SszContainerSchema<ImmutableSubContainerImpl> SSZ_SCHEMA =
        SszContainerSchema.create(
            "ImmutableSubContainer",
            List.of(
                NamedSchema.of("long", SszPrimitiveSchemas.UINT64_SCHEMA),
                NamedSchema.of("bytes", SszPrimitiveSchemas.BYTES32_SCHEMA)),
            ImmutableSubContainerImpl::new);

    private ImmutableSubContainerImpl(
        SszContainerSchema<ImmutableSubContainerImpl> type, TreeNode backingNode) {
      super(type, backingNode);
    }

    public ImmutableSubContainerImpl(UInt64 long1, Bytes32 bytes1) {
      super(SSZ_SCHEMA, SszUInt64.of(long1), SszBytes32.of(bytes1));
    }

    @Override
    public UInt64 getLong1() {
      return ((SszUInt64) get(0)).get();
    }

    @Override
    public Bytes32 getBytes1() {
      return ((SszBytes32) get(1)).get();
    }
  }

  public static class SubContainerReadImpl extends SszContainerImpl
      implements WritableSubContainer {

    public SubContainerReadImpl(TreeNode backingNode, IntCache<SszData> cache) {
      super(SSZ_SCHEMA, backingNode, cache);
    }

    private SubContainerReadImpl(
        SszContainerSchema<WritableSubContainer> type, TreeNode backingNode) {
      super(type, backingNode);
    }

    @Override
    public WritableMutableSubContainer createWritableCopy() {
      return new SubContainerWriteImpl(this);
    }
  }

  public static class SubContainerWriteImpl extends SszMutableContainerImpl
      implements WritableMutableSubContainer {

    public SubContainerWriteImpl(SubContainerReadImpl backingImmutableView) {
      super(backingImmutableView);
    }

    @Override
    protected SubContainerReadImpl createImmutableSszComposite(
        TreeNode backingNode, IntCache<SszData> viewCache) {
      return new SubContainerReadImpl(backingNode, viewCache);
    }

    @Override
    public WritableSubContainer commitChanges() {
      return (WritableSubContainer) super.commitChanges();
    }
  }

  public static class ContainerReadImpl extends SszContainerImpl implements WritableContainer {

    public ContainerReadImpl(SszContainerSchema<?> type, TreeNode backingNode) {
      super(type, backingNode);
    }

    public ContainerReadImpl(
        SszCompositeSchema<?> type, TreeNode backingNode, IntCache<SszData> cache) {
      super(type, backingNode, cache);
    }

    @Override
    public WritableMutableContainer createWritableCopy() {
      return new ContainerWriteImpl(this);
    }
  }

  public static class ContainerWriteImpl extends SszMutableContainerImpl
      implements WritableMutableContainer {

    public ContainerWriteImpl(ContainerReadImpl backingImmutableView) {
      super(backingImmutableView);
    }

    @Override
    protected ContainerReadImpl createImmutableSszComposite(
        TreeNode backingNode, IntCache<SszData> viewCache) {
      return new ContainerReadImpl(getSchema(), backingNode, viewCache);
    }

    @Override
    public WritableContainer commitChanges() {
      return (WritableContainer) super.commitChanges();
    }

    @Override
    public WritableMutableContainer createWritableCopy() {
      throw new UnsupportedOperationException();
    }

    @Override
    public WritableMutableSubContainer getSub1() {
      return (WritableMutableSubContainer) getByRef(2);
    }

    @Override
    @SuppressWarnings("unchecked")
    public SszMutableList<SszUInt64> getList1() {
      return (SszMutableList<SszUInt64>) getByRef(3);
    }

    @Override
    @SuppressWarnings("unchecked")
    public SszMutableRefList<WritableSubContainer, WritableMutableSubContainer> getList2() {
      return (SszMutableRefList<WritableSubContainer, WritableMutableSubContainer>) getByRef(4);
    }

    @Override
    @SuppressWarnings("unchecked")
    public SszMutableVector<ImmutableSubContainer> getVector1() {
      return (SszMutableVector<ImmutableSubContainer>) getByRef(5);
    }

    @Override
    public void setLong1(UInt64 val) {
      set(0, SszUInt64.of(val));
    }

    @Override
    public void setLong2(UInt64 val) {
      set(1, SszUInt64.of(val));
    }
  }
}
