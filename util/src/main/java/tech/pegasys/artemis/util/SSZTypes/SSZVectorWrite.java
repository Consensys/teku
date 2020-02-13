package tech.pegasys.artemis.util.SSZTypes;

public interface SSZVectorWrite<R> extends SSZVectorRead<R> {

  @Override
  R set(int index, R element);

}
