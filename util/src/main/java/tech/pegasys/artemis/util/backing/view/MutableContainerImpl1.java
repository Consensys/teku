package tech.pegasys.artemis.util.backing.view;

import java.util.function.Consumer;
import tech.pegasys.artemis.util.backing.ContainerViewRead;
import tech.pegasys.artemis.util.backing.ContainerViewWrite;
import tech.pegasys.artemis.util.backing.ContainerViewWriteRef;
import tech.pegasys.artemis.util.backing.ViewRead;
import tech.pegasys.artemis.util.backing.ViewWrite;
import tech.pegasys.artemis.util.backing.tree.TreeNode;
import tech.pegasys.artemis.util.backing.type.CompositeViewType;
import tech.pegasys.artemis.util.backing.type.ContainerViewType;

public abstract class MutableContainerImpl1<
        C extends MutableContainerImpl1<C, R, W>, R extends ContainerViewRead, W extends ContainerViewWriteRef>
    implements ContainerViewRead, ContainerViewWriteRef {

  private final ContainerViewRead readDelegate;
  private final ContainerViewWriteRef writeDelegate;

  protected MutableContainerImpl1(ContainerViewRead readDelegate,
      ContainerViewWriteRef writeDelegate) {
    this.readDelegate = readDelegate;
    this.writeDelegate = writeDelegate;
  }

  protected MutableContainerImpl1(
      ContainerViewType<? extends ContainerViewWrite> type, TreeNode backingNode) {
    this(new ContainerViewReadImpl(type, backingNode), null);
  }

  protected abstract C create(ContainerViewRead readDelegate, ContainerViewWriteRef writeDelegate);

  protected boolean isRead() {
    return writeDelegate == null;
  }

  protected ViewRead getOrGetByRef(int index) {
    return isRead() ? get(index) : getByRef(index);
  }

  /** Read methods **/

  @Override
  public ViewRead get(int index) {
    return readDelegate.get(index);
  }

  @Override
  public CompositeViewType getType() {
    return readDelegate.getType();
  }

  @Override
  public W createWritableCopy() {
    ContainerViewWriteRef write = (ContainerViewWriteRef) readDelegate.createWritableCopy();
    return (W) create(write, write);
  }

  @Override
  public TreeNode getBackingNode() {
    if (writeDelegate != null) {
      throw new IllegalStateException();
    } else {
      return readDelegate.getBackingNode();
    }
  }

  /** Write methods **/

  @Override
  public void setInvalidator(Consumer<ViewWrite> listener) {
    if (writeDelegate == null) {
      throw new IllegalStateException();
    } else {
      writeDelegate.setInvalidator(listener);
    }
  }

  @Override
  public void clear() {
    if (writeDelegate == null) {
      throw new IllegalStateException();
    } else {
      writeDelegate.clear();
    }
  }

  @Override
  public void set(int index, ViewRead value) {
    if (writeDelegate == null) {
      throw new IllegalStateException();
    } else {
      writeDelegate.set(index, value);
    }
  }

  @Override
  public ViewWrite getByRef(int index) {
    if (writeDelegate == null) {
      throw new IllegalStateException();
    } else {
      return writeDelegate.getByRef(index);
    }
  }

  @Override
  public R commitChanges() {
    if (writeDelegate == null) {
      throw new IllegalStateException();
    } else {
      return (R) create(writeDelegate.commitChanges(), null);
    }
  }
}
