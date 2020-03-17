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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.util.backing.tree.TreeNode;
import tech.pegasys.artemis.util.backing.tree.TreeUtil;
import tech.pegasys.artemis.util.backing.type.BasicViewTypes;
import tech.pegasys.artemis.util.backing.type.CompositeViewType;
import tech.pegasys.artemis.util.backing.type.ContainerViewType;
import tech.pegasys.artemis.util.backing.type.ListViewType;
import tech.pegasys.artemis.util.backing.type.VectorViewType;
import tech.pegasys.artemis.util.backing.view.AbstractCompositeViewRead;
import tech.pegasys.artemis.util.backing.view.AbstractImmutableContainer;
import tech.pegasys.artemis.util.backing.view.BasicViews.Bytes32View;
import tech.pegasys.artemis.util.backing.view.BasicViews.UInt64View;
import tech.pegasys.artemis.util.backing.view.ContainerViewReadImpl;
import tech.pegasys.artemis.util.backing.view.ContainerViewWriteImpl;
import tech.pegasys.artemis.util.backing.view.MutableContainerImpl1;

public class ContainerViewTest {
  private static final Logger LOG = LogManager.getLogger();

  public interface ImmutableSubContainer extends ContainerViewRead {

    UnsignedLong getLong1();

    Bytes32 getBytes1();
  }

  public interface SubContainerRead extends ContainerViewRead {

    UnsignedLong getLong1();

    UnsignedLong getLong2();
  }

  public interface SubContainerWrite extends SubContainerRead, ContainerViewWriteRef {

    void setLong1(UnsignedLong val);

    void setLong2(UnsignedLong val);
  }

  public interface ContainerRead extends ContainerViewRead {

    static ContainerRead createDefault() {
      return ContainerReadImpl.TYPE.getDefault();
    }

    UnsignedLong getLong1();

    UnsignedLong getLong2();

    SubContainerRead getSub1();

    ListViewRead<UInt64View> getList1();

    ListViewRead<SubContainerRead> getList2();

    VectorViewRead<ImmutableSubContainer> getList3();

    @Override
    ContainerWrite createWritableCopy();
  }

  public interface ContainerWrite extends ContainerRead, ContainerViewWriteRef {

    void setLong1(UnsignedLong val);

    void setLong2(UnsignedLong val);

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

  public static class ImmutableSubContainerImpl
      extends AbstractImmutableContainer<ImmutableSubContainerImpl>
      implements ImmutableSubContainer {

    public static final ContainerViewType<ImmutableSubContainerImpl> TYPE =
        new ContainerViewType<>(
            List.of(BasicViewTypes.UINT64_TYPE, BasicViewTypes.BYTES32_TYPE),
            ImmutableSubContainerImpl::new);

    private ImmutableSubContainerImpl(
        ContainerViewType<ImmutableSubContainerImpl> type, TreeNode backingNode) {
      super(type, backingNode);
    }

    public ImmutableSubContainerImpl(UnsignedLong long1, Bytes32 bytes1) {
      super(TYPE, new UInt64View(long1), new Bytes32View(bytes1));
    }

    @Override
    public UnsignedLong getLong1() {
      return ((UInt64View) get(0)).get();
    }

    @Override
    public Bytes32 getBytes1() {
      return ((Bytes32View) get(1)).get();
    }
  }

  public static class SubContainerImpl extends MutableContainerImpl1<SubContainerImpl, SubContainerRead, SubContainerWrite>
      implements SubContainerWrite {

    public static final ContainerViewType<SubContainerImpl> TYPE =
        new ContainerViewType<>(
            List.of(BasicViewTypes.UINT64_TYPE, BasicViewTypes.UINT64_TYPE), SubContainerImpl::new);

    public SubContainerImpl(ContainerViewRead readDelegate,
        ContainerViewWriteRef writeDelegate) {
      super(readDelegate, writeDelegate);
    }

    private SubContainerImpl(ContainerViewType<SubContainerImpl> type, TreeNode backingNode) {
      super(type, backingNode);
    }

    @Override
    protected SubContainerImpl create(
        ContainerViewRead readDelegate, ContainerViewWriteRef writeDelegate) {
      return new SubContainerImpl(readDelegate, writeDelegate);
    }

    @Override
    public UnsignedLong getLong1() {
      return ((UInt64View) get(0)).get();
    }

    @Override
    public UnsignedLong getLong2() {
      return ((UInt64View) get(1)).get();
    }

    @Override
    public void setLong1(UnsignedLong val) {
      set(0, new UInt64View(val));
    }

    @Override
    public void setLong2(UnsignedLong val) {
      set(1, new UInt64View(val));
    }
  }

  public static class ContainerReadImpl extends ContainerViewReadImpl
      implements ContainerRead {

    public static final ContainerViewType<ContainerReadImpl> TYPE =
        new ContainerViewType<>(
            List.of(
                BasicViewTypes.UINT64_TYPE,
                BasicViewTypes.UINT64_TYPE,
                SubContainerImpl.TYPE,
                new ListViewType<>(BasicViewTypes.UINT64_TYPE, 10),
                new ListViewType<>(SubContainerImpl.TYPE, 2),
                new VectorViewType<>(ImmutableSubContainerImpl.TYPE, 2)),
            ContainerReadImpl::new);

    public ContainerReadImpl(ContainerViewType<?> type, TreeNode backingNode) {
      super(type, backingNode);
    }

    public ContainerReadImpl(
        CompositeViewType type, TreeNode backingNode, ArrayList<ViewRead> cache) {
      super(type, backingNode, cache);
    }

    @Override
    public ContainerWrite createWritableCopy() {
      return new ContainerWriteImpl(this);
    }

    @Override
    public UnsignedLong getLong1() {
      return ((UInt64View) get(0)).get();
    }

    @Override
    public UnsignedLong getLong2() {
      return ((UInt64View) get(1)).get();
    }

    @Override
    public SubContainerRead getSub1() {
      return (SubContainerRead) get(2);
    }

    @Override
    @SuppressWarnings("unchecked")
    public ListViewRead<UInt64View> getList1() {
      return (ListViewRead<UInt64View>) get(3);
    }

    @Override
    @SuppressWarnings("unchecked")
    public ListViewRead<SubContainerRead> getList2() {
      return (ListViewRead<SubContainerRead>) get(4);
    }

    @Override
    @SuppressWarnings("unchecked")
    public VectorViewRead<ImmutableSubContainer> getList3() {
      return (VectorViewRead<ImmutableSubContainer>) get(5);
    }
  }

  public static class ContainerWriteImpl extends ContainerViewWriteImpl
      implements ContainerWrite {

    public ContainerWriteImpl(ContainerReadImpl backingImmutableView) {
      super(backingImmutableView);
    }

    @Override
    protected AbstractCompositeViewRead<?, ViewRead> createViewRead(TreeNode backingNode,
        ArrayList<ViewRead> viewCache) {
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

    //    @Override
//    public ContainerViewWriteImpl createWritableCopy() {
//      return super.createWritableCopy();
//    }

    @Override
    public UnsignedLong getLong1() {
      return ((UInt64View) get(0)).get();
    }

    @Override
    public UnsignedLong getLong2() {
      return ((UInt64View) get(1)).get();
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
    public void setLong1(UnsignedLong val) {
      set(0, new UInt64View(val));
    }

    @Override
    public void setLong2(UnsignedLong val) {
      set(1, new UInt64View(val));
    }
  }

  @Test
  public void readWriteContainerTest1() {
    ContainerRead c1 = ContainerRead.createDefault();

    {
      assertThat(c1.getSub1().getLong1()).isEqualTo(UnsignedLong.ZERO);
      assertThat(c1.getList1().isEmpty()).isTrue();
      assertThat(c1.getList2().isEmpty()).isTrue();
      assertThat(c1.getList3().get(0).getLong1()).isEqualTo(UnsignedLong.ZERO);
      assertThat(c1.getList3().get(0).getBytes1()).isEqualTo(Bytes32.ZERO);
      assertThat(c1.getList3().get(1).getLong1()).isEqualTo(UnsignedLong.ZERO);
      assertThat(c1.getList3().get(1).getBytes1()).isEqualTo(Bytes32.ZERO);
      assertThatExceptionOfType(IndexOutOfBoundsException.class)
          .isThrownBy(
              () -> {
                c1.getList3().get(2);
              });
    }

    ContainerWrite c1w = c1.createWritableCopy();
    c1w.setLong1(UnsignedLong.valueOf(0x1));
    c1w.setLong2(UnsignedLong.valueOf(0x2));

    c1w.getSub1().setLong1(UnsignedLong.valueOf(0x111));
    c1w.getSub1().setLong2(UnsignedLong.valueOf(0x222));

    c1w.getList1().append(UInt64View.fromLong(0x333));
    c1w.getList1().append(UInt64View.fromLong(0x444));

    c1w.getList2()
        .append(
            sc -> {
              sc.setLong1(UnsignedLong.valueOf(0x555));
              sc.setLong2(UnsignedLong.valueOf(0x666));
            });
    SubContainerWrite sc1w = c1w.getList2().append();
    sc1w.setLong1(UnsignedLong.valueOf(0x777));
    sc1w.setLong2(UnsignedLong.valueOf(0x888));

    c1w.getList3()
        .set(
            1,
            new ImmutableSubContainerImpl(
                UnsignedLong.valueOf(0x999), Bytes32.leftPad(Bytes.fromHexString("0xa999"))));

    {
      assertThat(c1.getSub1().getLong1()).isEqualTo(UnsignedLong.ZERO);
      assertThat(c1.getList1().isEmpty()).isTrue();
      assertThat(c1.getList2().isEmpty()).isTrue();
      assertThat(c1.getList3().get(0).getLong1()).isEqualTo(UnsignedLong.ZERO);
      assertThat(c1.getList3().get(0).getBytes1()).isEqualTo(Bytes32.ZERO);
      assertThat(c1.getList3().get(1).getLong1()).isEqualTo(UnsignedLong.ZERO);
      assertThat(c1.getList3().get(1).getBytes1()).isEqualTo(Bytes32.ZERO);

      assertThat(c1w.getLong1()).isEqualTo(UnsignedLong.valueOf(0x1));
      assertThat(c1w.getLong2()).isEqualTo(UnsignedLong.valueOf(0x2));
      assertThat(c1w.getSub1().getLong1()).isEqualTo(UnsignedLong.valueOf(0x111));
      assertThat(c1w.getSub1().getLong2()).isEqualTo(UnsignedLong.valueOf(0x222));
      assertThat(c1w.getList1().size()).isEqualTo(2);
      assertThat(c1w.getList1().get(0).get()).isEqualTo(UnsignedLong.valueOf(0x333));
      assertThat(c1w.getList1().get(1).get()).isEqualTo(UnsignedLong.valueOf(0x444));
      assertThatExceptionOfType(IndexOutOfBoundsException.class)
          .isThrownBy(
              () -> {
                c1w.getList1().get(2);
              });
      assertThat(c1w.getList2().size()).isEqualTo(2);
      assertThat(c1w.getList2().get(0).getLong1()).isEqualTo(UnsignedLong.valueOf(0x555));
      assertThat(c1w.getList2().get(0).getLong2()).isEqualTo(UnsignedLong.valueOf(0x666));
      assertThat(c1w.getList2().get(1).getLong1()).isEqualTo(UnsignedLong.valueOf(0x777));
      assertThat(c1w.getList2().get(1).getLong2()).isEqualTo(UnsignedLong.valueOf(0x888));
      assertThat(c1w.getList3().get(0).getLong1()).isEqualTo(UnsignedLong.ZERO);
      assertThat(c1w.getList3().get(0).getBytes1()).isEqualTo(Bytes32.ZERO);
      assertThat(c1w.getList3().get(1).getLong1()).isEqualTo(UnsignedLong.valueOf(0x999));
      assertThat(c1w.getList3().get(1).getBytes1())
          .isEqualTo(Bytes32.leftPad(Bytes.fromHexString("0xa999")));
    }

    ContainerRead c1r = c1w.commitChanges();
    LOG.error("\n" + TreeUtil.dumpBinaryTree(c1r.getBackingNode()));

    {
      assertThat(c1.getSub1().getLong1()).isEqualTo(UnsignedLong.ZERO);
      assertThat(c1.getList1().isEmpty()).isTrue();
      assertThat(c1.getList2().isEmpty()).isTrue();
      assertThat(c1.getList3().get(0).getLong1()).isEqualTo(UnsignedLong.ZERO);
      assertThat(c1.getList3().get(0).getBytes1()).isEqualTo(Bytes32.ZERO);
      assertThat(c1.getList3().get(1).getLong1()).isEqualTo(UnsignedLong.ZERO);
      assertThat(c1.getList3().get(1).getBytes1()).isEqualTo(Bytes32.ZERO);

      assertThat(c1r.getLong1()).isEqualTo(UnsignedLong.valueOf(0x1));
      assertThat(c1r.getLong2()).isEqualTo(UnsignedLong.valueOf(0x2));
      assertThat(c1r.getSub1().getLong1()).isEqualTo(UnsignedLong.valueOf(0x111));
      assertThat(c1r.getSub1().getLong2()).isEqualTo(UnsignedLong.valueOf(0x222));
      assertThat(c1r.getList1().size()).isEqualTo(2);
      assertThat(c1r.getList1().get(0).get()).isEqualTo(UnsignedLong.valueOf(0x333));
      assertThat(c1r.getList1().get(1).get()).isEqualTo(UnsignedLong.valueOf(0x444));
      assertThatExceptionOfType(IndexOutOfBoundsException.class)
          .isThrownBy(
              () -> {
                c1r.getList1().get(2);
              });
      assertThat(c1r.getList2().size()).isEqualTo(2);
      assertThat(c1r.getList2().get(0).getLong1()).isEqualTo(UnsignedLong.valueOf(0x555));
      assertThat(c1r.getList2().get(0).getLong2()).isEqualTo(UnsignedLong.valueOf(0x666));
      assertThat(c1r.getList2().get(1).getLong1()).isEqualTo(UnsignedLong.valueOf(0x777));
      assertThat(c1r.getList2().get(1).getLong2()).isEqualTo(UnsignedLong.valueOf(0x888));
      assertThat(c1r.getList3().get(0).getLong1()).isEqualTo(UnsignedLong.ZERO);
      assertThat(c1r.getList3().get(0).getBytes1()).isEqualTo(Bytes32.ZERO);
      assertThat(c1r.getList3().get(1).getLong1()).isEqualTo(UnsignedLong.valueOf(0x999));
      assertThat(c1r.getList3().get(1).getBytes1())
          .isEqualTo(Bytes32.leftPad(Bytes.fromHexString("0xa999")));
    }

    ContainerWrite c2w = c1r.createWritableCopy();
    c2w.getList2().getByRef(1).setLong2(UnsignedLong.valueOf(0xaaa));
    ContainerRead c2r = c2w.commitChanges();

    assertThat(c1r.getList2().get(1).getLong2()).isEqualTo(UnsignedLong.valueOf(0x888));
    assertThat(c2r.getList2().get(1).getLong2()).isEqualTo(UnsignedLong.valueOf(0xaaa));
  }
}
