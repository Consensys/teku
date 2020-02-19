package tech.pegasys.artemis.util.SSZTypes;

import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.apache.tuweni.bytes.Bytes32;
import org.jetbrains.annotations.NotNull;

public interface SSZImmutableCollection<E> extends Iterable<E> {

  /**
   * Returns the number of elements in this list.  If this list contains
   * more than {@code Integer.MAX_VALUE} elements, returns
   * {@code Integer.MAX_VALUE}.
   *
   * @return the number of elements in this list
   */
  int size();

  /**
   * Returns {@code true} if this list contains no elements.
   *
   * @return {@code true} if this list contains no elements
   */
  default boolean isEmpty() {
    return size() == 0;
  }

  /**
   * Returns the element at the specified position in this list.
   *
   * @param index index of the element to return
   * @return the element at the specified position in this list
   * @throws IndexOutOfBoundsException if the index is out of range
   *         ({@code index < 0 || index >= size()})
   */
  E get(int index);

  default Stream<E> stream() {
      return StreamSupport.stream(spliterator(), false);
  }

  Class<? extends E> getElementType();

  Bytes32 hash_tree_root();

  long getMaxSize();

  @Override
  default Spliterator<E> spliterator() {
    return Spliterators.spliterator(iterator(), size(), 0);
  }

  @NotNull
  @Override
  default Iterator<E> iterator() {
    return new Iterator<E>() {
      int index = 0;

      @Override
      public boolean hasNext() {
        return index < size();
      }

      @Override
      public E next() {
        return get(index++);
      }
    };
  }

  default List<E> asList() {
    return stream().collect(Collectors.toList());
  }
}
