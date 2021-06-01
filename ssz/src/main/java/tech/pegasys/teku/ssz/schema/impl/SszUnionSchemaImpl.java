package tech.pegasys.teku.ssz.schema.impl;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Objects;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.ssz.SszData;
import tech.pegasys.teku.ssz.SszUnion;
import tech.pegasys.teku.ssz.impl.SszUnionImpl;
import tech.pegasys.teku.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.schema.SszSchema;
import tech.pegasys.teku.ssz.schema.SszUnionSchema;
import tech.pegasys.teku.ssz.sos.SszDeserializeException;
import tech.pegasys.teku.ssz.sos.SszReader;
import tech.pegasys.teku.ssz.sos.SszWriter;
import tech.pegasys.teku.ssz.tree.BranchNode;
import tech.pegasys.teku.ssz.tree.GIndexUtil;
import tech.pegasys.teku.ssz.tree.LeafDataNode;
import tech.pegasys.teku.ssz.tree.LeafNode;
import tech.pegasys.teku.ssz.tree.TreeNode;

public class SszUnionSchemaImpl implements SszUnionSchema<SszUnion> {

  private static final int MAX_SELECTOR = 127;
  private static final int DEFAULT_SELECTOR = 0;

  private static LeafNode createSelectorNode(int selector) {
    assert selector <= MAX_SELECTOR;
    return LeafNode.create(Bytes.of((byte) selector));
  }

  private final List<SszSchema<?>> childrenSchemas;
  private final TreeNode defaultTree;

  public SszUnionSchemaImpl(List<SszSchema<?>> childrenSchemas) {
    checkArgument(childrenSchemas.size() > 1, "At least two types expected in Union");
    checkArgument(childrenSchemas.size() < MAX_SELECTOR, "Too many child types");
    checkArgument(childrenSchemas.stream().skip(1)
            .allMatch(schema -> schema != SszPrimitiveSchemas.NONE_SCHEMA),
        "None is allowed for zero selector only");
    this.childrenSchemas = childrenSchemas;
    defaultTree = createUnionNode(childrenSchemas.get(DEFAULT_SELECTOR).getDefaultTree(),
        DEFAULT_SELECTOR);
  }

  @Override
  public List<SszSchema<?>> getChildrenSchemas() {
    return childrenSchemas;
  }

  @Override
  public boolean isPrimitive() {
    return false;
  }

  @Override
  public TreeNode getDefaultTree() {
    return defaultTree;
  }

  @Override
  public SszUnion createFromBackingNode(TreeNode node) {
    return new SszUnionImpl(this, node);
  }

  @Override
  public SszUnion createFromValue(int selector, SszData value) {
    checkArgument(selector < getTypesCount(), "Selector is out of bounds");
    checkArgument(getChildSchema(selector).equals(value.getSchema()),
        "Incompatible value schema for supplied selector");
    return createFromBackingNode(createUnionNode(value.getBackingNode(), selector));
  }

  @Override
  public int getSszVariablePartSize(TreeNode node) {
    int selector = getSelector(node);
    return childrenSchemas.get(selector).getSszSize(getValueNode(node));
  }

  @Override
  public int sszSerializeTree(TreeNode node, SszWriter writer) {
    int selector = getSelector(node);
    writer.write(Bytes.of(selector));
    SszSchema<?> valueSchema = childrenSchemas.get(selector);
    int valueSszLength = valueSchema.sszSerializeTree(getValueNode(node), writer);
    return valueSszLength + SELECTOR_SIZE_BYTES;
  }

  @Override
  public TreeNode sszDeserializeTree(SszReader reader) throws SszDeserializeException {
    int selector = reader.read(1).get(0) & 0xFF;
    if (selector >= getTypesCount()) {
      throw new SszDeserializeException(
          "Invalid selector " + selector + " for Union schema: " + this);
    }
    SszSchema<?> valueSchema = childrenSchemas.get(selector);
    TreeNode valueNode = valueSchema.sszDeserializeTree(reader);

    return createUnionNode(valueNode, selector);
  }

  public int getSelectorFromSelectorNode(TreeNode selectorNode) {
    checkArgument(selectorNode instanceof LeafDataNode, "Invalid selector node");
    LeafDataNode dataNode = (LeafDataNode) selectorNode;
    Bytes bytes = dataNode.getData();
    checkArgument(bytes.size() == SELECTOR_SIZE_BYTES, "Invalid selector node");
    int selector = bytes.get(0) & 0xFF;
    checkArgument(selector <= MAX_SELECTOR, "Selector exceeds max value");
    return selector;
  }

  public TreeNode getValueNode(TreeNode unionNode) {
    return unionNode.get(GIndexUtil.LEFT_CHILD_G_INDEX);
  }

  public int getSelector(TreeNode unionNode) {
    int selector = getSelectorFromSelectorNode(unionNode.get(GIndexUtil.RIGHT_CHILD_G_INDEX));
    checkArgument(selector < getTypesCount(), "Selector is out of bounds");
    return selector;
  }

  private TreeNode createUnionNode(TreeNode valueNode, int selector) {
    return BranchNode.create(valueNode, createSelectorNode(selector));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof SszUnionSchemaImpl)) {
      return false;
    }
    SszUnionSchemaImpl that = (SszUnionSchemaImpl) o;
    return Objects.equal(childrenSchemas, that.childrenSchemas);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(childrenSchemas);
  }

  @Override
  public String toString() {
    return "Union" + childrenSchemas;
  }
}
