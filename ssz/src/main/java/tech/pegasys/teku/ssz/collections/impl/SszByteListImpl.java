package tech.pegasys.teku.ssz.collections.impl;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.ssz.cache.IntCache;
import tech.pegasys.teku.ssz.collections.SszByteList;
import tech.pegasys.teku.ssz.primitive.SszByte;
import tech.pegasys.teku.ssz.schema.collections.SszByteListSchema;
import tech.pegasys.teku.ssz.schema.collections.impl.SszByteListSchemaImpl;
import tech.pegasys.teku.ssz.tree.TreeNode;

public class SszByteListImpl extends SszPrimitiveListImpl<Byte, SszByte> implements SszByteList {

  private final Bytes data;

  public SszByteListImpl(SszByteListSchema<?> schema, Bytes bytes) {
    super(schema, () -> SszByteListSchemaImpl.fromBytesToTree(schema, bytes));
    this.data = bytes;
  }

  public SszByteListImpl(SszByteListSchema<?> schema, TreeNode backingTree) {
    super(schema, backingTree);
    this.data = SszByteListSchemaImpl.fromTreeToBytes(schema, backingTree);
  }

  @Override
  public Bytes getBytes() {
    return data;
  }

  @Override
  protected IntCache<SszByte> createCache() {
    // caching with Bytes in this class
    return IntCache.noop();
  }

  @Override
  public SszByteListSchemaImpl<?> getSchema() {
    return (SszByteListSchemaImpl<?>) super.getSchema();
  }

  @Override
  public boolean isWritableSupported() {
    return false;
  }

  @Override
  public String toString() {
    return "SszByteList{" + data + '}';
  }
}
