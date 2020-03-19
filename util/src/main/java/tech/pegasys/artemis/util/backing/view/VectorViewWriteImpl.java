package tech.pegasys.artemis.util.backing.view;

import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import tech.pegasys.artemis.util.backing.VectorViewWrite;
import tech.pegasys.artemis.util.backing.VectorViewWriteRef;
import tech.pegasys.artemis.util.backing.ViewRead;
import tech.pegasys.artemis.util.backing.tree.TreeNode;
import tech.pegasys.artemis.util.backing.tree.TreeNodes;
import tech.pegasys.artemis.util.backing.tree.TreeUtil;
import tech.pegasys.artemis.util.backing.type.VectorViewType;
import tech.pegasys.artemis.util.backing.type.ViewType;
import tech.pegasys.artemis.util.cache.Cache;

public class VectorViewWriteImpl<R extends ViewRead, W extends R>
    extends AbstractCompositeViewWrite1<VectorViewWriteImpl<R, W>, R, W>
    implements VectorViewWriteRef<R, W> {


  public VectorViewWriteImpl(AbstractCompositeViewRead<?, R> backingImmutableView) {
    super(backingImmutableView);
  }

  @Override
  protected AbstractCompositeViewRead<?, R> createViewRead(
      TreeNode backingNode, Cache<Integer, R> viewCache) {
    return new VectorViewReadImpl<>(getType(), backingNode, viewCache);
  }

  @Override
  public VectorViewType<R> getType() {
    return (VectorViewType<R>) super.getType();
  }

  @Override
  public VectorViewReadImpl<R> commitChanges() {
    return (VectorViewReadImpl<R>) super.commitChanges();
  }

  protected TreeNodes packChanges(List<Entry<Integer, R>> newChildValues, TreeNode original) {
    VectorViewType<R> type = getType();
    ViewType elementType = type.getElementType();
    int elementsPerChunk = type.getElementsPerChunk();
    TreeNodes ret = new TreeNodes();

    newChildValues.stream()
        .collect(Collectors.groupingBy(e -> e.getKey() / elementsPerChunk))
        .forEach((nodeIndex, nodeVals) -> {
          long gIndex = type.getGeneralizedIndex(nodeIndex);
          // optimization: when all packed values changed no need to retrieve original node to
          // merge with
          TreeNode node =
              nodeVals.size() == elementsPerChunk
                  ? TreeUtil.ZERO_LEAF
                  : original.get(gIndex);
          for (Entry<Integer, R> entry : nodeVals) {
            node =
                elementType.updateBackingNode(
                    node, entry.getKey() % elementsPerChunk, entry.getValue());
          }
          ret.add(gIndex, node);
        });
    return ret;
  }

  @Override
  protected void checkIndex(int index, boolean set) {
    if (index >= size()) {
      throw new IndexOutOfBoundsException(
          "Invalid index " + index + " for vector with size " + size());
    }
  }

  @Override
  public VectorViewWrite<R> createWritableCopy() {
    throw new UnsupportedOperationException();
  }
}
