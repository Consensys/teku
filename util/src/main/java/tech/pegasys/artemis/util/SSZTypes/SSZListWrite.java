package tech.pegasys.artemis.util.SSZTypes;

public interface SSZListWrite<R> extends SSZListRead<R> {

  @Override
  boolean add(R r);

  @Override
  R set(int index, R element);
}
