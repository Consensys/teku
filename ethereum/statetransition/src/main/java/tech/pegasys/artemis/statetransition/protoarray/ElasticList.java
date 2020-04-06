package tech.pegasys.artemis.statetransition.protoarray;

import java.util.ArrayList;
import java.util.Collections;

public class ElasticList<T> extends ArrayList<T> {

  private T defaultObject;

  public ElasticList(T defaultObject) {
    super();
    this.defaultObject = defaultObject;
  }

  private void ensure(int i) {
    if (super.size() <= i) {
      super.addAll(Collections.nCopies(i - super.size() + 1, defaultObject));
    }
  }

  @Override
  public T get(int i) {
    ensure(i);
    return super.get(i);
  }
}
