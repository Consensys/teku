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

package tech.pegasys.artemis.statetransition.protoarray;

import java.util.ArrayList;
import java.util.Collections;

public class ElasticList<T> extends ArrayList<T> {

  private T defaultObject;

  public ElasticList(T defaultObject) {
    super();
    this.defaultObject = defaultObject;
  }

  private void ensure(int i) {
    if (super.size() <= i) {
      super.addAll(Collections.nCopies(i - super.size() + 1, defaultObject));
    }
  }

  @Override
  public T get(int i) {
    ensure(i);
    return super.get(i);
  }
}
