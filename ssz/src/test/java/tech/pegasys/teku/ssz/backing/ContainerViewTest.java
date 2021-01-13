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
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.tree.TreeUtil;
import tech.pegasys.teku.ssz.backing.type.BasicViewTypes;
import tech.pegasys.teku.ssz.backing.type.CompositeViewType;
import tech.pegasys.teku.ssz.backing.type.ContainerViewType;
import tech.pegasys.teku.ssz.backing.type.ListViewType;
import tech.pegasys.teku.ssz.backing.type.VectorViewType;
import tech.pegasys.teku.ssz.backing.view.AbstractCompositeViewRead;
import tech.pegasys.teku.ssz.backing.view.AbstractImmutableContainer;
import tech.pegasys.teku.ssz.backing.view.BasicViews.Bytes32View;
import tech.pegasys.teku.ssz.backing.view.BasicViews.UInt64View;
import tech.pegasys.teku.ssz.backing.view.ContainerViewReadImpl;
import tech.pegasys.teku.ssz.backing.view.ContainerViewWriteImpl;
import tech.pegasys.teku.ssz.sos.SszTypeDescriptor;

public class ContainerViewTest {
  private static final Logger LOG = LogManager.getLogger();

  public interface ImmutableSubContainer extends ContainerViewRead {

    UInt64 getLong1();

    Bytes32 getBytes1();
  }

  public interface SubContainerRead extends ContainerViewRead {

    @SszTypeDescriptor
    ContainerViewType<SubContainerRead> TYPE =
        ContainerViewType.create(
            List.of(BasicViewTypes.UINT64_TYPE, BasicViewTypes.UINT64_TYPE),
            SubContainerReadImpl::new);

    default UInt64 getLong1() {
      return ((UInt64View) get(0)).get();
    }

    default UInt64 getLong2() {
      return ((UInt64View) get(1)).get();
    }
  }

  public interface SubContainerWrite extends SubContainerRead, ContainerViewWriteRef {

    default void setLong1(UInt64 val) {
      set(0, new UInt64View(val));
    }

    default void setLong2(UInt64 val) {
      set(1, new UInt64View(val));
    }
  }

  public interface ContainerRead extends ContainerViewRead {

    @SszTypeDescriptor
    ContainerViewType<ContainerReadImpl> TYPE =
        ContainerViewType.create(
            List.of(
                BasicViewTypes.UINT64_TYPE,
                BasicViewTypes.UINT64_TYPE,
                SubContainerRead.TYPE,
                new ListViewType<>(BasicViewTypes.UINT64_TYPE, 10),
                new ListViewType<>(SubContainerRead.TYPE, 2),
                new VectorViewType<>(ImmutableSubContainerImpl.TYPE, 2)),
            ContainerReadImpl::new);

    static ContainerRead createDefault() {
      return ContainerReadImpl.TYPE.getDefault();
    }

    default UInt64 getLong1() {
      return ((UInt64View) get(0)).get();
    }

    default UInt64 getLong2() {
      return ((UInt64View) get(1)).get();
    }

    default SubContainerRead getSub1() {
      return (SubContainerRead) get(2);
    }

    default ListViewRead<UInt64View> getList1() {
      return getAny(3);
    }

    default ListViewRead<SubContainerRead> getList2() {
      return getAny(4);
    }

    default VectorViewRead<ImmutableSubContainer> getList3() {
      return getAny(5);
    }

    @Override
    ContainerWrite createWritableCopy();
  }

  public interface ContainerWrite extends ContainerRead, ContainerViewWriteRef {

    void setLong1(UInt64 val);

    void setLong2(UInt64 val);

    @Override
    SubContainerWrite getSub1();

    @Override
    ListViewWrite<UInt64View> getList1();

    @Override
    ListViewWriteRef<SubContainerRead, SubContainerWrite> getList2();

    @Override
    VectorViewWrite<ImmutableSubContainer> getList3();

    @Override
    ContainerRead commitChanges();
  }

  public static class ImmutableSubContainerImpl extends AbstractImmutableContainer
      implements ImmutableSubContainer {

    @SszTypeDescriptor
    public static final ContainerViewType<ImmutableSubContainerImpl> TYPE =
        ContainerViewType.create(
            List.of(BasicViewTypes.UINT64_TYPE, BasicViewTypes.BYTES32_TYPE),
            ImmutableSubContainerImpl::new);

    private ImmutableSubContainerImpl(
        ContainerViewType<ImmutableSubContainerImpl> type, TreeNode backingNode) {
      super(type, backingNode);
    }

    public ImmutableSubContainerImpl(UInt64 long1, Bytes32 bytes1) {
      super(TYPE, new UInt64View(long1), new Bytes32View(bytes1));
    }

    @Override
    public UInt64 getLong1() {
      return ((UInt64View) get(0)).get();
    }

    @Override
    public Bytes32 getBytes1() {
      return ((Bytes32View) get(1)).get();
    }
  }

  public static class SubContainerReadImpl extends ContainerViewReadImpl
      implements SubContainerRead {

    public SubContainerReadImpl(TreeNode backingNode, IntCache<ViewRead> cache) {
      super(TYPE, backingNode, cache);
    }

    private SubContainerReadImpl(ContainerViewType<SubContainerRead> type, TreeNode backingNode) {
      super(type, backingNode);
    }

    @Override
    public SubContainerWrite createWritableCopy() {
      return new SubContainerWriteImpl(this);
    }
  }

  public static class SubContainerWriteImpl extends ContainerViewWriteImpl
      implements SubContainerWrite {

    public SubContainerWriteImpl(SubContainerReadImpl backingImmutableView) {
      super(backingImmutableView);
    }

    @Override
    protected AbstractCompositeViewRead<ViewRead> createViewRead(
        TreeNode backingNode, IntCache<ViewRead> viewCache) {
      return new SubContainerReadImpl(backingNode, viewCache);
    }

    @Override
    public SubContainerRead commitChanges() {
      return (SubContainerRead) super.commitChanges();
    }
  }

  public static class ContainerReadImpl extends ContainerViewReadImpl implements ContainerRead {

    public ContainerReadImpl(ContainerViewType<?> type, TreeNode backingNode) {
      super(type, backingNode);
    }

    public ContainerReadImpl(
        CompositeViewType<?> type, TreeNode backingNode, IntCache<ViewRead> cache) {
      super(type, backingNode, cache);
    }

    @Override
    public ContainerWrite createWritableCopy() {
      return new ContainerWriteImpl(this);
    }
  }

  public static class ContainerWriteImpl extends ContainerViewWriteImpl implements ContainerWrite {

    public ContainerWriteImpl(ContainerReadImpl backingImmutableView) {
      super(backingImmutableView);
    }

    @Override
    protected AbstractCompositeViewRead<ViewRead> createViewRead(
        TreeNode backingNode, IntCache<ViewRead> viewCache) {
      return new ContainerReadImpl(getType(), backingNode, viewCache);
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
    public ListViewWrite<UInt64View> getList1() {
      return (ListViewWrite<UInt64View>) getByRef(3);
    }

    @Override
    @SuppressWarnings("unchecked")
    public ListViewWriteRef<SubContainerRead, SubContainerWrite> getList2() {
      return (ListViewWriteRef<SubContainerRead, SubContainerWrite>) getByRef(4);
    }

    @Override
    @SuppressWarnings("unchecked")
    public VectorViewWrite<ImmutableSubContainer> getList3() {
      return (VectorViewWrite<ImmutableSubContainer>) getByRef(5);
    }

    @Override
    public void setLong1(UInt64 val) {
      set(0, new UInt64View(val));
    }

    @Override
    public void setLong2(UInt64 val) {
      set(1, new UInt64View(val));
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

    c1w.getList1().append(UInt64View.fromLong(0x333));
    c1w.getList1().append(UInt64View.fromLong(0x444));

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
    LOG.error("\n" + TreeUtil.dumpBinaryTree(c1r.getBackingNode()));

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

    c1w.getList1().append(UInt64View.fromLong(0x333));
    c1w.getList1().append(UInt64View.fromLong(0x444));

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
    assertThat(Utils.equalsByGetters(c1r, c1w)).isTrue();
    ContainerWrite c2w = c1r.createWritableCopy();
    c2w.getList2().getByRef(0).setLong1(UInt64.valueOf(293874));
    assertThat(Utils.equalsByGetters(c1r, c2w)).isFalse();
    assertThat(Utils.equalsByGetters(c1r, c2w.commitChanges())).isFalse();

    // new container from backing tree without any cached views
    ContainerRead c2r = ContainerRead.TYPE.createFromBackingNode(c1r.getBackingNode());
    // concurrently traversing children of the the same view instance to make sure the internal
    // cache is thread safe
    List<Future<Boolean>> futures =
        TestUtil.executeParallel(() -> Utils.equalsByGetters(c2r, c1r), 512);

    assertThat(TestUtil.waitAll(futures)).containsOnly(true);

    Consumer<ContainerWrite> containerMutator =
        w -> {
          w.setLong2(UInt64.valueOf(0x11111));
          w.getSub1().setLong2(UInt64.valueOf(0x22222));
          w.getList1().append(UInt64View.fromLong(0x44444));
          w.getList1().set(0, UInt64View.fromLong(0x11111));
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

    ContainerRead c4r = ContainerRead.TYPE.createFromBackingNode(c1r.getBackingNode());

    assertThat(Utils.equalsByGetters(c1r, c4r)).isTrue();
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
    assertThat(Utils.equalsByGetters(c1r, c4r)).isTrue();
    assertThat(c1r.hashTreeRoot()).isEqualTo(c4r.hashTreeRoot());

    assertThat(modified)
        .allMatch(
            c -> Utils.equalsByGetters(c, c3r) && c.hashTreeRoot().equals(c3r.hashTreeRoot()));
  }
}
