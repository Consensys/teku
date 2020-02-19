/*
 * Copyright 2020 ConsenSys AG.
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

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.util.backing.ListViewWriteRef;
import tech.pegasys.artemis.util.backing.ViewRead;

public class SSZBackingListRef<R extends ViewRead, W extends R> extends SSZAbstractCollection<R>
    implements SSZMutableRefList<R, W> {

  private final ListViewWriteRef<R, W> delegate;

  public SSZBackingListRef(Class<? extends R> classInfo, ListViewWriteRef<R, W> delegate) {
    super(classInfo);
    this.delegate = delegate;
  }

  @Override
  public W get(int index) {
    return delegate.getByRef(index);
  }

  @Override
  public int size() {
    return delegate.size();
  }

  @Override
  public long getMaxSize() {
    return delegate.getType().getMaxLength();
  }

  @Override
  public void add(R c) {
    delegate.append(c);
  }

  @Override
  public void set(int index, R element) {
    delegate.set(index, element);
  }

  @Override
  public void clear() {
    delegate.clear();
  }

  public Bytes32 hash_tree_root() {
    return delegate.hashTreeRoot();
  }
}
