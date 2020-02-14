package tech.pegasys.artemis.util.SSZTypes;

public interface SSZListRead<R> extends SSZList<R> {

  @Override
  long getMaxSize();

  @Override
  Class<? extends R> getElementType();

  @Override
  int size();

  @Override
  boolean isEmpty();

  @Override
  boolean contains(Object o);

  @Override
  R get(int index);
}
