package tech.pegasys.artemis.util.SSZTypes;

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

}
