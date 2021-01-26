package tech.pegasys.teku.ssz.backing;

import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.jetbrains.annotations.NotNull;

public interface CollectionViewRead<ElementT extends ViewRead> extends CompositeViewRead<ElementT>,
    Iterable<ElementT> {

  default boolean isEmpty() {
    return size() == 0;
  }

  default Stream<ElementT> stream() {
    return StreamSupport.stream(spliterator(), false);
  }

  @NotNull
  @Override
  default Iterator<ElementT> iterator() {
    return new Iterator<>() {
      int index = 0;

      @Override
      public boolean hasNext() {
        return index < size();
      }

      @Override
      public ElementT next() {
        return get(index++);
      }
    };
  }
}
