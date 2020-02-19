package tech.pegasys.artemis.util.SSZTypes;

import java.util.Iterator;
import java.util.stream.Collectors;

public abstract class SSZAbstractCollection<C> implements SSZMutableCollection<C> {

  protected final Class<? extends C> classInfo;

  public SSZAbstractCollection(Class<? extends C> classInfo) {
    this.classInfo = classInfo;
  }

  @Override
  public Class<? extends C> getElementType() {
    return classInfo;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof Iterable){
      Iterator<?> thisIt = iterator();
      Iterator<?> thatIt = ((Iterable) obj).iterator();
      while(thisIt.hasNext()){
        if(!thatIt.hasNext() || !thisIt.next().equals(thatIt.next())){
          return false;
        }
      }
      return !thatIt.hasNext();
    } else {
      return false;
    }
  }

  @Override
  public String toString() {
    return "[" + stream().map(Object::toString).collect(Collectors.joining(", ")) + "]";
  }
}
