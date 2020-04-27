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

package tech.pegasys.teku.ssz.SSZTypes;

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

  private final boolean isVector;
  protected final long maxSize;
  protected final List<T> data;

  SSZArrayCollection(Class<? extends T> classInfo, long maxSize, boolean isVector) {
    super(classInfo);
    this.data = new ArrayList<>();
    this.maxSize = maxSize;
    this.isVector = isVector;
  }

  SSZArrayCollection(
      List<? extends T> elements, long maxSize, Class<? extends T> classInfo, boolean isVector) {
    super(classInfo);
    this.data = new ArrayList<>(elements);
    this.maxSize = maxSize;
    this.isVector = isVector;
  }

  @SuppressWarnings("unchecked")
  SSZArrayCollection(int size, T object, boolean isVector) {
    super((Class<? extends T>) object.getClass());
    this.data = new ArrayList<>(Collections.nCopies(size, object));
    this.maxSize = size;
    this.isVector = isVector;
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
    } else {
      throw new IllegalArgumentException("List out of bounds");
    }
  }

  @Override
  public long getMaxSize() {
    return maxSize;
  }

  @Override
  public int size() {
    return isVector ? (int) getMaxSize() : data.size();
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
