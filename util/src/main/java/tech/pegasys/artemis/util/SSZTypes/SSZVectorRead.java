package tech.pegasys.artemis.util.SSZTypes;

import org.apache.tuweni.bytes.Bytes32;

public interface SSZVectorRead<R> extends SSZVector<R> {

  @Override
  Class<R> getElementType();

  @Override
  int size();

  @Override
  boolean isEmpty();

  @Override
  boolean contains(Object o);

  @Override
  R get(int index);

  Bytes32 hash_tree_root();
}
