package tech.pegasys.artemis.util.SSZTypes;

public interface SSZMutableCollection<E> extends SSZImmutableCollection<E> {

  /**
   * Removes all of the elements from this collection (optional operation).
   * The collection will be empty after this method returns.
   *
   * @throws UnsupportedOperationException if the {@code clear} operation
   *         is not supported by this collection
   */
  void clear();

  /**
   * Replaces the element at the specified position in this list with the
   * specified element (optional operation).
   *
   * @param index index of the element to replace
   * @param element element to be stored at the specified position
   */
  void set(int index, E element);

  default void setAll(SSZImmutableCollection<? extends E> other) {
    clear();
    for (int i = 0; i < other.size(); i++) {
      set(i, other.get(i));
    }
  }
}
