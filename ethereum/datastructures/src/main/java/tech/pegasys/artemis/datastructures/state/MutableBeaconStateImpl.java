package tech.pegasys.artemis.datastructures.state;

import jdk.jfr.Label;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.util.backing.ViewRead;
import tech.pegasys.artemis.util.backing.tree.TreeNode;
import tech.pegasys.artemis.util.backing.view.ContainerViewWriteImpl;
import tech.pegasys.artemis.util.cache.Cache;

class MutableBeaconStateImpl extends ContainerViewWriteImpl
    implements MutableBeaconState, BeaconStateCache {

  static MutableBeaconStateImpl createBuilder() {
    return new MutableBeaconStateImpl(new BeaconStateImpl(), true);
  }

  @Label("sos-ignore")
  private final TransitionCaches transitionCaches;

  @Label("sos-ignore")
  private final boolean builder;

  MutableBeaconStateImpl(BeaconStateImpl backingImmutableView) {
    this(backingImmutableView, false);
  }

  MutableBeaconStateImpl(BeaconStateImpl backingImmutableView, boolean builder) {
    super(backingImmutableView);
    this.transitionCaches =
        builder ? TransitionCaches.getNoOp() : backingImmutableView.getTransitionCaches().copy();
    this.builder = builder;
  }

  @Override
  protected BeaconStateImpl createViewRead(TreeNode backingNode, Cache<Integer, ViewRead> viewCache) {
    return new BeaconStateImpl(
        backingNode, viewCache, builder ? TransitionCaches.createNewEmpty() : transitionCaches);
  }

  @Override
  public TransitionCaches getTransitionCaches() {
    return transitionCaches;
  }

  @Override
  public BeaconState commitChanges() {
    return (BeaconState) super.commitChanges();
  }

  @Override
  public Bytes32 hash_tree_root() {
    // TODO optimize
    return commitChanges().hash_tree_root();
  }

  @Override
  public int getSSZFieldCount() {
    throw new UnsupportedOperationException();
  }

  @Override
  public MutableBeaconState createWritableCopy() {
    return (MutableBeaconState) super.createWritableCopy();
  }
}
