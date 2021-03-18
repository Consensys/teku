package tech.pegasys.teku.ssz.collections.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import tech.pegasys.teku.ssz.SszPrimitive;
import tech.pegasys.teku.ssz.collections.SszMutablePrimitiveCollection;
import tech.pegasys.teku.ssz.collections.SszPrimitiveCollection;
import tech.pegasys.teku.ssz.impl.AbstractSszComposite;
import tech.pegasys.teku.ssz.impl.AbstractSszMutableCollection;
import tech.pegasys.teku.ssz.schema.SszCollectionSchema;
import tech.pegasys.teku.ssz.schema.SszPrimitiveSchema;
import tech.pegasys.teku.ssz.schema.SszPrimitiveSchema.PackedNodeUpdate;
import tech.pegasys.teku.ssz.tree.LeafNode;
import tech.pegasys.teku.ssz.tree.TreeNode;
import tech.pegasys.teku.ssz.tree.TreeUpdates;

public abstract class AbstractSszMutablePrimitiveCollection<
    ElementT, SszElementT extends SszPrimitive<ElementT, SszElementT>>
  extends AbstractSszMutableCollection<SszElementT, SszElementT>
  implements SszMutablePrimitiveCollection<ElementT, SszElementT> {

  private final SszPrimitiveSchema<ElementT, SszElementT> elementSchemaCache;

  public AbstractSszMutablePrimitiveCollection(
      AbstractSszComposite<SszElementT> backingImmutableData) {
    super(backingImmutableData);
    elementSchemaCache = (SszPrimitiveSchema<ElementT, SszElementT>) getSchema().getElementSchema();
  }

  @Override
  public SszPrimitiveSchema<ElementT, SszElementT> getPrimitiveElementSchema() {
    return elementSchemaCache;
  }

  @Override
  protected void validateChildSchema(int index, SszElementT value) {
    // no need to check primitive value schema
  }

  /**
   * Overridden to pack primitive values to tree nodes.
   * E.g. a tree node can contain up to 8 {@link tech.pegasys.teku.ssz.primitive.SszBytes4} values
   */
  @Override
  protected TreeUpdates changesToNewNodes(Stream<Entry<Integer, SszElementT>> newChildValues,
      TreeNode original) {
    SszCollectionSchema<?, ?> type = getSchema();
    int elementsPerChunk = type.getElementsPerChunk();

    List<Entry<Integer, SszElementT>> newChildren = newChildValues.collect(Collectors.toList());
    int prevChildNodeIndex = 0;
    List<NodeUpdate<ElementT, SszElementT>> nodeUpdates = new ArrayList<>();
    NodeUpdate<ElementT, SszElementT> curNodeUpdate = null;

    for (Map.Entry<Integer, SszElementT> entry : newChildren) {
      int childIndex = entry.getKey();
      int childNodeIndex = childIndex / elementsPerChunk;

      if (curNodeUpdate == null || childNodeIndex != prevChildNodeIndex) {
        long gIndex = type.getChildGeneralizedIndex(childNodeIndex);
        curNodeUpdate = new NodeUpdate<>(gIndex, elementsPerChunk);
        nodeUpdates.add(curNodeUpdate);
        prevChildNodeIndex = childNodeIndex;
      }
      curNodeUpdate.addUpdate(childIndex % elementsPerChunk, entry.getValue());
    }

    List<Long> gIndexes = new ArrayList<>();
    List<TreeNode> newValues = new ArrayList<>();
    SszPrimitiveSchema<ElementT, SszElementT> elementType = getPrimitiveElementSchema();
    for (NodeUpdate<ElementT, SszElementT> nodeUpdate : nodeUpdates) {
      long gIndex = nodeUpdate.getNodeGIndex();
      TreeNode originalNode =
          nodeUpdate.getUpdates().size() < elementsPerChunk
              ? original.get(gIndex)
              : LeafNode.EMPTY_LEAF;
      TreeNode newNode =
          elementType.updatePackedNode(
              originalNode,
              nodeUpdate.getUpdates());
      newValues.add(newNode);
      gIndexes.add(gIndex);
    }

    return new TreeUpdates(gIndexes, newValues);
  }

  @Override
  public SszPrimitiveCollection<ElementT, SszElementT> commitChanges() {
    return (SszPrimitiveCollection<ElementT, SszElementT>) super.commitChanges();
  }

  @Override
  public SszMutablePrimitiveCollection<
      ElementT, SszElementT> createWritableCopy() {
    throw new UnsupportedOperationException();
  }


  private static class NodeUpdate<
      ElementT, SszElementT extends SszPrimitive<ElementT, SszElementT>> {
    private final List<PackedNodeUpdate<ElementT, SszElementT>> updates;
    private final long nodeGIndex;

    public NodeUpdate(long nodeGIndex, int maxElementsPerChunk) {
      this.updates = new ArrayList<>(maxElementsPerChunk);
      this.nodeGIndex = nodeGIndex;
    }

    public void addUpdate(int internalNodeIndex, SszElementT newValue) {
      updates.add(new PackedNodeUpdate<>(internalNodeIndex, newValue));
    }

    public long getNodeGIndex() {
      return nodeGIndex;
    }

    public List<PackedNodeUpdate<ElementT, SszElementT>> getUpdates() {
      return updates;
    }
  }
}
