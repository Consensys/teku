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

package tech.pegasys.artemis.util.SSZTypes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.jetbrains.annotations.NotNull;

public class SSZArrayCollection<T> extends SSZAbstractCollection<T>
    implements SSZMutableList<T>, SSZMutableVector<T> {

  protected final long maxSize;
  protected final List<T> data;

  SSZArrayCollection(Class<? extends T> classInfo, long maxSize) {
    super(classInfo);
    this.data = new ArrayList<>();
    this.maxSize = maxSize;
  }

  SSZArrayCollection(List<? extends T> elements, long maxSize, Class<? extends T> classInfo) {
    super(classInfo);
    this.data = new ArrayList<>(elements);
    this.maxSize = maxSize;
  }

  @SuppressWarnings("unchecked")
  SSZArrayCollection(int size, T object) {
    super((Class<? extends T>) object.getClass());
    this.data = Collections.nCopies(size, object);
    this.maxSize = size;
  }

  @Override
  public void clear() {
    data.clear();
  }

  @Override
  public void set(int index, T element) {
    data.set(index, element);
  }

  @Override
  public void add(T object) {
    if (data.size() < maxSize) {
      data.add(object);
    }
  }

  @Override
  public long getMaxSize() {
    return maxSize;
  }

  @Override
  public int size() {
    return data.size();
  }

  @Override
  public T get(int index) {
    return data.get(index);
  }

  @Override
  public Stream<T> stream() {
    return data.stream();
  }

  @Override
  public Bytes32 hash_tree_root() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public Iterator<T> iterator() {
    return data.iterator();
  }

  @Override
  public int hashCode() {
    return Objects.hash(maxSize, classInfo, data);
  }
}
