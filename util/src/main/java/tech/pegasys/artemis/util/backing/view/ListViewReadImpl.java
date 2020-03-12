package tech.pegasys.artemis.util.backing.view;

import java.util.concurrent.atomic.AtomicInteger;
import tech.pegasys.artemis.util.backing.ContainerViewWrite;
import tech.pegasys.artemis.util.backing.ListViewRead;
import tech.pegasys.artemis.util.backing.ListViewWrite;
import tech.pegasys.artemis.util.backing.ViewRead;
import tech.pegasys.artemis.util.backing.tree.TreeNode;
import tech.pegasys.artemis.util.backing.type.ListViewType;

public class ListViewReadImpl<C extends ViewRead> implements ListViewRead<C> {

  private final ContainerViewWrite container;
  private final AtomicInteger size;

  @Override
  public ListViewWrite<C> createWritableCopy() {
    return null;
  }

  @Override
  public ListViewType<C> getType() {
    return null;
  }

  @Override
  public int size() {
    return 0;
  }

  @Override
  public C get(int index) {
    return null;
  }

  @Override
  public TreeNode getBackingNode() {
    return null;
  }
}
