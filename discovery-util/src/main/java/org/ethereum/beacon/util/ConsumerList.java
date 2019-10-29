/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.ethereum.beacon.util;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class ConsumerList<E> implements List<E> {

  @VisibleForTesting final int maxSize;
  private final LinkedList<E> delegate;
  private final Consumer<E> spillOutConsumer;

  private ConsumerList(int maxSize, Consumer<E> spillOutConsumer) {
    checkArgument(maxSize >= 0, "maxSize (%s) must >= 0", maxSize);
    this.delegate = new LinkedList<E>();
    this.maxSize = maxSize;
    this.spillOutConsumer = spillOutConsumer;
  }

  /**
   * Creates and returns a new consumer list that will hold up to {@code maxSize} elements.
   *
   * <p>When {@code maxSize} is zero, elements will be consumed immediately after being added to the
   * queue.
   */
  public static <E> ConsumerList<E> create(int maxSize, Consumer<E> spillOutConsumer) {
    return new ConsumerList<>(maxSize, spillOutConsumer);
  }

  /**
   * Returns the number of additional elements that this list can accept without consuming; zero if
   * the list is currently full.
   *
   * @since 16.0
   */
  public int remainingCapacity() {
    return maxSize - size();
  }

  protected List<E> delegate() {
    return delegate;
  }

  /**
   * Adds the given element to this queue. If the queue is currently full, the element at the head
   * of the queue is consumed to make room.
   *
   * @return {@code true} always
   */
  @Override
  public boolean add(E e) {
    checkNotNull(e); // check before removing
    if (maxSize == 0) {
      spillOutConsumer.accept(e);
      return true;
    }
    if (size() == maxSize) {
      spillOutConsumer.accept(delegate.remove());
    }
    delegate.add(e);
    return true;
  }

  @Override
  public boolean addAll(Collection<? extends E> collection) {
    int size = collection.size();
    if (size >= maxSize) {
      while (!delegate.isEmpty()) {
        spillOutConsumer.accept(delegate.remove());
      }
      int numberToSkip = size - maxSize;
      Iterator<? extends E> iterator = collection.iterator();
      int i = 0;
      while (iterator.hasNext() && i < numberToSkip) {
        spillOutConsumer.accept(iterator.next());
        ++i;
      }
      return Iterables.addAll(this, Iterables.skip(collection, numberToSkip));
    }
    return Iterators.addAll(this, collection.iterator());
  }

  @Override
  public int size() {
    return delegate.size();
  }

  @Override
  public boolean contains(Object o) {
    return delegate.contains(o);
  }

  @Override
  public E get(int index) {
    return delegate.get(index);
  }

  @Override
  public Stream<E> stream() {
    return delegate.stream();
  }

  @Override
  public boolean isEmpty() {
    return delegate.isEmpty();
  }

  @Override
  public Iterator<E> iterator() {
    return delegate.iterator();
  }

  @Override
  public Object[] toArray() {
    return delegate.toArray();
  }

  @Override
  public <T> T[] toArray(T[] a) {
    return delegate.toArray(a);
  }

  @Override
  public boolean remove(Object o) {
    return delegate.remove(o);
  }

  @Override
  public boolean containsAll(Collection<?> c) {
    return delegate.containsAll(c);
  }

  @Override
  public boolean addAll(int index, Collection<? extends E> c) {
    throw new RuntimeException("Not implemented yet!");
  }

  @Override
  public boolean removeAll(Collection<?> c) {
    return delegate.removeAll(c);
  }

  @Override
  public boolean retainAll(Collection<?> c) {
    return delegate.retainAll(c);
  }

  @Override
  public void clear() {
    delegate.clear();
  }

  @Override
  public E set(int index, E element) {
    throw new RuntimeException("Not implememnted yet!");
  }

  @Override
  public void add(int index, E element) {
    throw new RuntimeException("Not implememnted yet!");
  }

  @Override
  public E remove(int index) {
    return delegate.remove(index);
  }

  @Override
  public int indexOf(Object o) {
    return delegate.indexOf(o);
  }

  @Override
  public int lastIndexOf(Object o) {
    return delegate.lastIndexOf(o);
  }

  @Override
  public ListIterator<E> listIterator() {
    return delegate.listIterator();
  }

  @Override
  public ListIterator<E> listIterator(int index) {
    return delegate.listIterator(index);
  }

  @Override
  public List<E> subList(int fromIndex, int toIndex) {
    ConsumerList<E> res = create(maxSize, spillOutConsumer);
    res.addAll(delegate.subList(fromIndex, toIndex));
    return res;
  }
}
