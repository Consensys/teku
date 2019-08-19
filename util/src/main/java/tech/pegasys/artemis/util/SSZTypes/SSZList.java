package tech.pegasys.artemis.util.SSZTypes;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("rawtypes")
public class SSZList<T> extends ArrayList<T> {

  private long maxSize;
  private Class classInfo;

  public SSZList() throws UnsupportedOperationException {
    throw new UnsupportedOperationException("SSZList must have specified max size");
  }

  public SSZList(Class classInfo, long maxSize) {
    super();
    this.classInfo = classInfo;
    this.maxSize = maxSize;
  }

  public SSZList(SSZList<T> list) {
    super(list);
    maxSize = list.getMaxSize();
    this.classInfo = list.getElementType();
  }

  public SSZList(List<T> list, long maxSize, Class classInfo) {
    super(list);
    this.maxSize = maxSize;
    this.classInfo = classInfo;
  }


    @Override
  public boolean add(T object) {
    if (super.size() < maxSize) {
      return super.add(object);
    } else {
      return false;
    }
  }

  public long getMaxSize() {
    return maxSize;
  }

  public Class getElementType() {
    return classInfo;
  }
}
