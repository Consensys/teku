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
import tech.pegasys.teku.ssz.backing.VectorViewRead;
import tech.pegasys.teku.ssz.backing.VectorViewWrite;
import tech.pegasys.teku.ssz.backing.ViewRead;

public class SSZBackingVector<C, R extends ViewRead> extends SSZAbstractCollection<C>
    implements SSZMutableVector<C> {

  private final VectorViewRead<R> delegate;
  private final Function<C, R> wrapper;
  private final Function<R, C> unwrapper;

  public SSZBackingVector(
      Class<C> classInfo,
      VectorViewRead<R> delegate,
      Function<C, R> wrapper,
      Function<R, C> unwrapper) {
    super(classInfo);
    this.delegate = delegate;
    this.wrapper = wrapper;
    this.unwrapper = unwrapper;
  }

  private VectorViewWrite<R> getWriteDelegate() {
    // temporary workaround to have a single implementation class
    return (VectorViewWrite<R>) delegate;
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
  public void set(int index, C element) {
    getWriteDelegate().set(index, wrapper.apply(element));
  }

  @Override
  public void clear() {
    getWriteDelegate().clear();
  }

  @Override
  public long getMaxSize() {
    return size();
  }

  @Override
  public Bytes32 hash_tree_root() {
    return delegate.hashTreeRoot();
  }
}
