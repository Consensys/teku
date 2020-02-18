package tech.pegasys.artemis.util.backing;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.util.backing.tree.TreeNode;
import tech.pegasys.artemis.util.backing.type.BasicViewTypes;
import tech.pegasys.artemis.util.backing.type.ContainerViewType;
import tech.pegasys.artemis.util.backing.type.ListViewType;
import tech.pegasys.artemis.util.backing.view.BasicViews.Bytes32View;
import tech.pegasys.artemis.util.backing.view.BasicViews.UInt64View;
import tech.pegasys.artemis.util.backing.view.ContainerViewImpl;

public class ListViewTest {

  public static class SubContainer extends ContainerViewImpl<SubContainer> {

    public static final ContainerViewType<SubContainer> TYPE =
        new ContainerViewType<>(
            List.of(BasicViewTypes.UINT64_TYPE, BasicViewTypes.BYTES32_TYPE),
            SubContainer::new);

    private SubContainer(
        ContainerViewType<SubContainer> type, TreeNode backingNode) {
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
    ListViewType<SubContainer> type = new ListViewType<>(SubContainer.TYPE,100);
    ListViewRead<SubContainer> lr1 = type.createDefault();
    ListViewWrite<SubContainer> lw1 = lr1.createWritableCopy();
    lw1.append(new SubContainer(UnsignedLong.valueOf(0x111), Bytes32.leftPad(Bytes.of(0x22))));
    lw1.append(new SubContainer(UnsignedLong.valueOf(0x111), Bytes32.leftPad(Bytes.of(0x22))));
    ListViewWrite<SubContainer> lw2 = lw1.commitChanges().createWritableCopy();
    lw2.clear();
    ListViewRead<SubContainer> lr2 = lw2.commitChanges();
    Assertions.assertEquals(lr1.hashTreeRoot(), lr2.hashTreeRoot());
  }
}
