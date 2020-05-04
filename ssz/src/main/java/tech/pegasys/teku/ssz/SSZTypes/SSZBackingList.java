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

package tech.pegasys.teku.ssz.SSZTypes;

import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ssz.backing.ListViewRead;
import tech.pegasys.teku.ssz.backing.ListViewWrite;
import tech.pegasys.teku.ssz.backing.ViewRead;

public class SSZBackingList<C, R extends ViewRead> extends SSZAbstractCollection<C>
    implements SSZMutableList<C> {

  private final ListViewRead<R> delegate;
  private final Function<C, R> wrapper;
  private final Function<R, C> unwrapper;

  public SSZBackingList(
      Class<? extends C> classInfo,
      ListViewRead<R> delegate,
      Function<C, R> wrapper,
      Function<R, C> unwrapper) {
    super(classInfo);
    this.delegate = delegate;
    this.wrapper = wrapper;
    this.unwrapper = unwrapper;
  }

  private ListViewWrite<R> getWriteDelegate() {
    // temporary workaround to have a single implementation class
    return (ListViewWrite<R>) delegate;
  }

  @Override
  public C get(int index) {
    return unwrapper.apply(delegate.get(index));
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
  public void add(C c) {
    getWriteDelegate().append(wrapper.apply(c));
  }

  @Override
  public void set(int index, C element) {
    getWriteDelegate().set(index, wrapper.apply(element));
  }

  @Override
  public void clear() {
    getWriteDelegate().clear();
  }

  @Override
  public Bytes32 hash_tree_root() {
    return delegate.hashTreeRoot();
  }
}
