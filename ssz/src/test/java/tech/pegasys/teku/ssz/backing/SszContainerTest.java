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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static tech.pegasys.teku.ssz.backing.SszDataAssert.assertThatSszData;

import java.util.List;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.TestUtil;
import tech.pegasys.teku.ssz.backing.cache.IntCache;
import tech.pegasys.teku.ssz.backing.schema.SszCompositeSchema;
import tech.pegasys.teku.ssz.backing.schema.SszContainerSchema;
import tech.pegasys.teku.ssz.backing.schema.SszListSchema;
import tech.pegasys.teku.ssz.backing.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.backing.schema.SszVectorSchema;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.view.AbstractSszImmutableContainer;
import tech.pegasys.teku.ssz.backing.view.SszContainerImpl;
import tech.pegasys.teku.ssz.backing.view.SszMutableContainerImpl;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszBytes32;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszUInt64;

public class SszContainerTest {
  private static final Logger LOG = LogManager.getLogger();

  public interface ImmutableSubContainer extends SszContainer {

    UInt64 getLong1();

    Bytes32 getBytes1();
  }

  public interface SubContainerRead extends SszContainer {

    SszContainerSchema<SubContainerRead> SSZ_SCHEMA =
        SszContainerSchema.create(
            List.of(SszPrimitiveSchemas.UINT64_SCHEMA, SszPrimitiveSchemas.UINT64_SCHEMA),
            SubContainerReadImpl::new);

    default UInt64 getLong1() {
      return ((SszUInt64) get(0)).get();
    }

    default UInt64 getLong2() {
      return ((SszUInt64) get(1)).get();
    }
  }

  public interface SubContainerWrite extends SubContainerRead, SszMutableRefContainer {

    default void setLong1(UInt64 val) {
      set(0, SszUInt64.of(val));
    }

    default void setLong2(UInt64 val) {
      set(1, SszUInt64.of(val));
    }
  }

  public interface ContainerRead extends SszContainer {

    SszContainerSchema<ContainerReadImpl> SSZ_SCHEMA =
        SszContainerSchema.create(
            List.of(
                SszPrimitiveSchemas.UINT64_SCHEMA,
                SszPrimitiveSchemas.UINT64_SCHEMA,
                SubContainerRead.SSZ_SCHEMA,
                SszListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 10),
                SszListSchema.create(SubContainerRead.SSZ_SCHEMA, 2),
                SszVectorSchema.create(ImmutableSubContainerImpl.SSZ_SCHEMA, 2)),
            ContainerReadImpl::new);

    static ContainerRead createDefault() {
      return ContainerReadImpl.SSZ_SCHEMA.getDefault();
    }

    default UInt64 getLong1() {
      return ((SszUInt64) get(0)).get();
    }

    default UInt64 getLong2() {
      return ((SszUInt64) get(1)).get();
    }

    default SubContainerRead getSub1() {
      return (SubContainerRead) get(2);
    }

    default SszList<SszUInt64> getList1() {
      return getAny(3);
    }

    default SszList<SubContainerRead> getList2() {
      return getAny(4);
    }

    default SszVector<ImmutableSubContainer> getList3() {
      return getAny(5);
    }

    @Override
    ContainerWrite createWritableCopy();
  }

  public interface ContainerWrite extends ContainerRead, SszMutableRefContainer {

    void setLong1(UInt64 val);

    void setLong2(UInt64 val);

    @Override
    SubContainerWrite getSub1();

    @Override
    SszMutableList<SszUInt64> getList1();

    @Override
    SszMutableRefList<SubContainerRead, SubContainerWrite> getList2();

    @Override
    SszMutableVector<ImmutableSubContainer> getList3();

    @Override
    ContainerRead commitChanges();
  }

  public static class ImmutableSubContainerImpl extends AbstractSszImmutableContainer
      implements ImmutableSubContainer {

    public static final SszContainerSchema<ImmutableSubContainerImpl> SSZ_SCHEMA =
        SszContainerSchema.create(
            List.of(SszPrimitiveSchemas.UINT64_SCHEMA, SszPrimitiveSchemas.BYTES32_SCHEMA),
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

  public static class SubContainerReadImpl extends SszContainerImpl implements SubContainerRead {

    public SubContainerReadImpl(TreeNode backingNode, IntCache<SszData> cache) {
      super(SSZ_SCHEMA, backingNode, cache);
    }

    private SubContainerReadImpl(SszContainerSchema<SubContainerRead> type, TreeNode backingNode) {
      super(type, backingNode);
    }

    @Override
    public SubContainerWrite createWritableCopy() {
      return new SubContainerWriteImpl(this);
    }
  }

  public static class SubContainerWriteImpl extends SszMutableContainerImpl
      implements SubContainerWrite {

    public SubContainerWriteImpl(SubContainerReadImpl backingImmutableView) {
      super(backingImmutableView);
    }

    @Override
    protected SubContainerReadImpl createImmutableSszComposite(
        TreeNode backingNode, IntCache<SszData> viewCache) {
      return new SubContainerReadImpl(backingNode, viewCache);
    }

    @Override
    public SubContainerRead commitChanges() {
      return (SubContainerRead) super.commitChanges();
    }
  }

  public static class ContainerReadImpl extends SszContainerImpl implements ContainerRead {

    public ContainerReadImpl(SszContainerSchema<?> type, TreeNode backingNode) {
      super(type, backingNode);
    }

    public ContainerReadImpl(
        SszCompositeSchema<?> type, TreeNode backingNode, IntCache<SszData> cache) {
      super(type, backingNode, cache);
    }

    @Override
    public ContainerWrite createWritableCopy() {
      return new ContainerWriteImpl(this);
    }
  }

  public static class ContainerWriteImpl extends SszMutableContainerImpl implements ContainerWrite {

    public ContainerWriteImpl(ContainerReadImpl backingImmutableView) {
      super(backingImmutableView);
    }

    @Override
    protected ContainerReadImpl createImmutableSszComposite(
        TreeNode backingNode, IntCache<SszData> viewCache) {
      return new ContainerReadImpl(getSchema(), backingNode, viewCache);
    }

    @Override
    public ContainerRead commitChanges() {
      return (ContainerRead) super.commitChanges();
    }

    @Override
    public ContainerWrite createWritableCopy() {
      throw new UnsupportedOperationException();
    }

    @Override
    public SubContainerWrite getSub1() {
      return (SubContainerWrite) getByRef(2);
    }

    @Override
    @SuppressWarnings("unchecked")
    public SszMutableList<SszUInt64> getList1() {
      return (SszMutableList<SszUInt64>) getByRef(3);
    }

    @Override
    @SuppressWarnings("unchecked")
    public SszMutableRefList<SubContainerRead, SubContainerWrite> getList2() {
      return (SszMutableRefList<SubContainerRead, SubContainerWrite>) getByRef(4);
    }

    @Override
    @SuppressWarnings("unchecked")
    public SszMutableVector<ImmutableSubContainer> getList3() {
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

  @Test
  public void readWriteContainerTest1() {
    ContainerRead c1 = ContainerRead.createDefault();

    {
      assertThat(c1.getSub1().getLong1()).isEqualTo(UInt64.ZERO);
      assertThat(c1.getList1().isEmpty()).isTrue();
      assertThat(c1.getList2().isEmpty()).isTrue();
      assertThat(c1.getList3().get(0).getLong1()).isEqualTo(UInt64.ZERO);
      assertThat(c1.getList3().get(0).getBytes1()).isEqualTo(Bytes32.ZERO);
      assertThat(c1.getList3().get(1).getLong1()).isEqualTo(UInt64.ZERO);
      assertThat(c1.getList3().get(1).getBytes1()).isEqualTo(Bytes32.ZERO);
      assertThatExceptionOfType(IndexOutOfBoundsException.class)
          .isThrownBy(
              () -> {
                c1.getList3().get(2);
              });
    }

    ContainerWrite c1w = c1.createWritableCopy();
    c1w.setLong1(UInt64.valueOf(0x1));
    c1w.setLong2(UInt64.valueOf(0x2));

    c1w.getSub1().setLong1(UInt64.valueOf(0x111));
    c1w.getSub1().setLong2(UInt64.valueOf(0x222));

    c1w.getList1().append(SszUInt64.of(UInt64.fromLongBits(0x333)));
    c1w.getList1().append(SszUInt64.of(UInt64.fromLongBits(0x444)));

    c1w.getList2()
        .append(
            sc -> {
              sc.setLong1(UInt64.valueOf(0x555));
              sc.setLong2(UInt64.valueOf(0x666));
            });
    SubContainerWrite sc1w = c1w.getList2().append();
    sc1w.setLong1(UInt64.valueOf(0x777));
    sc1w.setLong2(UInt64.valueOf(0x888));

    c1w.getList3()
        .set(
            1,
            new ImmutableSubContainerImpl(
                UInt64.valueOf(0x999), Bytes32.leftPad(Bytes.fromHexString("0xa999"))));

    {
      assertThat(c1.getSub1().getLong1()).isEqualTo(UInt64.ZERO);
      assertThat(c1.getList1().isEmpty()).isTrue();
      assertThat(c1.getList2().isEmpty()).isTrue();
      assertThat(c1.getList3().get(0).getLong1()).isEqualTo(UInt64.ZERO);
      assertThat(c1.getList3().get(0).getBytes1()).isEqualTo(Bytes32.ZERO);
      assertThat(c1.getList3().get(1).getLong1()).isEqualTo(UInt64.ZERO);
      assertThat(c1.getList3().get(1).getBytes1()).isEqualTo(Bytes32.ZERO);

      assertThat(c1w.getLong1()).isEqualTo(UInt64.valueOf(0x1));
      assertThat(c1w.getLong2()).isEqualTo(UInt64.valueOf(0x2));
      assertThat(c1w.getSub1().getLong1()).isEqualTo(UInt64.valueOf(0x111));
      assertThat(c1w.getSub1().getLong2()).isEqualTo(UInt64.valueOf(0x222));
      assertThat(c1w.getList1().size()).isEqualTo(2);
      assertThat(c1w.getList1().get(0).get()).isEqualTo(UInt64.valueOf(0x333));
      assertThat(c1w.getList1().get(1).get()).isEqualTo(UInt64.valueOf(0x444));
      assertThatExceptionOfType(IndexOutOfBoundsException.class)
          .isThrownBy(
              () -> {
                c1w.getList1().get(2);
              });
      assertThat(c1w.getList2().size()).isEqualTo(2);
      assertThat(c1w.getList2().get(0).getLong1()).isEqualTo(UInt64.valueOf(0x555));
      assertThat(c1w.getList2().get(0).getLong2()).isEqualTo(UInt64.valueOf(0x666));
      assertThat(c1w.getList2().get(1).getLong1()).isEqualTo(UInt64.valueOf(0x777));
      assertThat(c1w.getList2().get(1).getLong2()).isEqualTo(UInt64.valueOf(0x888));
      assertThat(c1w.getList3().get(0).getLong1()).isEqualTo(UInt64.ZERO);
      assertThat(c1w.getList3().get(0).getBytes1()).isEqualTo(Bytes32.ZERO);
      assertThat(c1w.getList3().get(1).getLong1()).isEqualTo(UInt64.valueOf(0x999));
      assertThat(c1w.getList3().get(1).getBytes1())
          .isEqualTo(Bytes32.leftPad(Bytes.fromHexString("0xa999")));
    }

    ContainerRead c1r = c1w.commitChanges();
    LOG.error("\n" + SszTestUtils.dumpBinaryTree(c1r.getBackingNode()));

    {
      assertThat(c1.getSub1().getLong1()).isEqualTo(UInt64.ZERO);
      assertThat(c1.getList1().isEmpty()).isTrue();
      assertThat(c1.getList2().isEmpty()).isTrue();
      assertThat(c1.getList3().get(0).getLong1()).isEqualTo(UInt64.ZERO);
      assertThat(c1.getList3().get(0).getBytes1()).isEqualTo(Bytes32.ZERO);
      assertThat(c1.getList3().get(1).getLong1()).isEqualTo(UInt64.ZERO);
      assertThat(c1.getList3().get(1).getBytes1()).isEqualTo(Bytes32.ZERO);

      assertThat(c1r.getLong1()).isEqualTo(UInt64.valueOf(0x1));
      assertThat(c1r.getLong2()).isEqualTo(UInt64.valueOf(0x2));
      assertThat(c1r.getSub1().getLong1()).isEqualTo(UInt64.valueOf(0x111));
      assertThat(c1r.getSub1().getLong2()).isEqualTo(UInt64.valueOf(0x222));
      assertThat(c1r.getList1().size()).isEqualTo(2);
      assertThat(c1r.getList1().get(0).get()).isEqualTo(UInt64.valueOf(0x333));
      assertThat(c1r.getList1().get(1).get()).isEqualTo(UInt64.valueOf(0x444));
      assertThatExceptionOfType(IndexOutOfBoundsException.class)
          .isThrownBy(
              () -> {
                c1r.getList1().get(2);
              });
      assertThat(c1r.getList2().size()).isEqualTo(2);
      assertThat(c1r.getList2().get(0).getLong1()).isEqualTo(UInt64.valueOf(0x555));
      assertThat(c1r.getList2().get(0).getLong2()).isEqualTo(UInt64.valueOf(0x666));
      assertThat(c1r.getList2().get(1).getLong1()).isEqualTo(UInt64.valueOf(0x777));
      assertThat(c1r.getList2().get(1).getLong2()).isEqualTo(UInt64.valueOf(0x888));
      assertThat(c1r.getList3().get(0).getLong1()).isEqualTo(UInt64.ZERO);
      assertThat(c1r.getList3().get(0).getBytes1()).isEqualTo(Bytes32.ZERO);
      assertThat(c1r.getList3().get(1).getLong1()).isEqualTo(UInt64.valueOf(0x999));
      assertThat(c1r.getList3().get(1).getBytes1())
          .isEqualTo(Bytes32.leftPad(Bytes.fromHexString("0xa999")));
    }

    ContainerWrite c2w = c1r.createWritableCopy();
    c2w.getList2().getByRef(1).setLong2(UInt64.valueOf(0xaaa));
    ContainerRead c2r = c2w.commitChanges();

    assertThat(c1r.getList2().get(1).getLong2()).isEqualTo(UInt64.valueOf(0x888));
    assertThat(c2r.getList2().get(1).getLong2()).isEqualTo(UInt64.valueOf(0xaaa));
  }

  // The threading test is probabilistic and may have false positives
  // (i.e. pass on incorrect implementation)
  @Test
  public void testThreadSafety() throws InterruptedException {
    ContainerWrite c1w = ContainerRead.createDefault().createWritableCopy();
    c1w.setLong1(UInt64.valueOf(0x1));
    c1w.setLong2(UInt64.valueOf(0x2));

    c1w.getSub1().setLong1(UInt64.valueOf(0x111));
    c1w.getSub1().setLong2(UInt64.valueOf(0x222));

    c1w.getList1().append(SszUInt64.of(UInt64.fromLongBits(0x333)));
    c1w.getList1().append(SszUInt64.of(UInt64.fromLongBits(0x444)));

    c1w.getList2()
        .append(
            sc -> {
              sc.setLong1(UInt64.valueOf(0x555));
              sc.setLong2(UInt64.valueOf(0x666));
            });

    c1w.getList3()
        .set(
            0,
            new ImmutableSubContainerImpl(
                UInt64.valueOf(0x999), Bytes32.leftPad(Bytes.fromHexString("0xa999"))));
    c1w.getList3()
        .set(
            1,
            new ImmutableSubContainerImpl(
                UInt64.valueOf(0xaaa), Bytes32.leftPad(Bytes.fromHexString("0xaaaa"))));

    ContainerRead c1r = c1w.commitChanges();

    // sanity check of equalsByGetters
    assertThatSszData(c1r).isEqualTo(c1w).isEqualByGettersTo(c1w).isEqualByHashTreeRootTo(c1w);
    ContainerWrite c2w = c1r.createWritableCopy();
    c2w.getList2().getByRef(0).setLong1(UInt64.valueOf(293874));
    assertThatSszData(c1r).isEqualTo(c1w).isEqualByGettersTo(c1w).isEqualByHashTreeRootTo(c1w);
    assertThatSszData(c1r).isNotEqualByAllMeansTo(c2w);
    assertThatSszData(c1r).isNotEqualByAllMeansTo(c2w.commitChanges());

    // new container from backing tree without any cached views
    ContainerRead c2r = ContainerRead.SSZ_SCHEMA.createFromBackingNode(c1r.getBackingNode());
    // concurrently traversing children of the the same view instance to make sure the internal
    // cache is thread safe
    List<Future<Boolean>> futures =
        TestUtil.executeParallel(() -> SszDataAssert.isEqualByGetters(c2r, c1r), 512);

    assertThat(TestUtil.waitAll(futures)).containsOnly(true);

    Consumer<ContainerWrite> containerMutator =
        w -> {
          w.setLong2(UInt64.valueOf(0x11111));
          w.getSub1().setLong2(UInt64.valueOf(0x22222));
          w.getList1().append(SszUInt64.of(UInt64.fromLongBits(0x44444)));
          w.getList1().set(0, SszUInt64.of(UInt64.fromLongBits(0x11111)));
          SubContainerWrite sc = w.getList2().append();
          sc.setLong1(UInt64.valueOf(0x77777));
          sc.setLong2(UInt64.valueOf(0x88888));
          w.getList2().getByRef(0).setLong2(UInt64.valueOf(0x44444));
          w.getList3()
              .set(
                  0,
                  new ImmutableSubContainerImpl(
                      UInt64.valueOf(0x99999), Bytes32.leftPad(Bytes.fromHexString("0xa99999"))));
        };
    ContainerWrite c3w = c1r.createWritableCopy();
    containerMutator.accept(c3w);
    ContainerRead c3r = c3w.commitChanges();

    ContainerRead c4r = ContainerRead.SSZ_SCHEMA.createFromBackingNode(c1r.getBackingNode());

    assertThatSszData(c1r).isEqualTo(c4r).isEqualByGettersTo(c4r).isEqualByHashTreeRootTo(c4r);
    // make updated view from the source view in parallel
    // this tests that mutable view caches are merged and transferred
    // in a thread safe way
    List<Future<ContainerRead>> modifiedFuts =
        TestUtil.executeParallel(
            () -> {
              ContainerWrite w = c4r.createWritableCopy();
              containerMutator.accept(w);
              return w.commitChanges();
            },
            512);

    List<ContainerRead> modified = TestUtil.waitAll(modifiedFuts);
    assertThatSszData(c4r).isEqualTo(c1r).isEqualByGettersTo(c1r).isEqualByHashTreeRootTo(c1r);

    assertThat(modified)
        .allSatisfy(
            c ->
                assertThatSszData(c)
                    .isEqualTo(c3r)
                    .isEqualByGettersTo(c3r)
                    .isEqualByHashTreeRootTo(c3r));
  }
}
