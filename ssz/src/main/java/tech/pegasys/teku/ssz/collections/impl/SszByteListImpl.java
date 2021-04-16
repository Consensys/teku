package tech.pegasys.teku.ssz.collections.impl;

import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes;
import tech.pegasys.teku.ssz.cache.IntCache;
import tech.pegasys.teku.ssz.collections.SszByteList;
import tech.pegasys.teku.ssz.primitive.SszByte;
import tech.pegasys.teku.ssz.schema.collections.SszByteListSchema;
import tech.pegasys.teku.ssz.schema.collections.impl.SszByteListSchemaImpl;
import tech.pegasys.teku.ssz.tree.TreeNode;

public class SszByteListImpl extends SszPrimitiveListImpl<Byte, SszByte> implements SszByteList {

  public SszByteListImpl(SszByteListSchema<?> schema, TreeNode backingTree) {
    super(schema, backingTree);
  }

  @Override
  public Bytes getBytes() {
    MutableBytes bytes = MutableBytes.create(size());
    IntStream.range(0, size()).forEach(idx -> bytes.set(idx, get(idx).get()));
    return bytes;
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
    return "SszByteList{" + getBytes() + '}';
  }
}
