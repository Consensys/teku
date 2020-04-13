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

package tech.pegasys.artemis.protoarray;

import java.util.ArrayList;
import java.util.function.Supplier;

class ElasticList<T> extends ArrayList<T> {

  private final Supplier<T> defaultObjectGenerator;

  public ElasticList(Supplier<T> defaultObjectGenerator) {
    super();
    this.defaultObjectGenerator = defaultObjectGenerator;
  }

  private void ensure(int i) {
    while (super.size() <= i) {
      super.add(defaultObjectGenerator.get());
    }
  }

  @Override
  public T get(int i) {
    ensure(i);
    return super.get(i);
  }
}
