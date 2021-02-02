/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.ssz.sos;

import java.util.function.Supplier;
import tech.pegasys.teku.ssz.backing.type.ViewType;

public class SszField {
  private final int index;
  private final String name;
  private final Supplier<ViewType<?>> viewType;

  public SszField(int index, ViewType<?> viewType) {
    this(index, () -> viewType);
  }

  public SszField(int index, Supplier<ViewType<?>> viewType) {
    this(index, "field-" + index, viewType);
  }

  public SszField(int index, String name, ViewType<?> viewType) {
    this(index, name, () -> viewType);
  }

  public SszField(int index, String name, Supplier<ViewType<?>> viewType) {
    this.index = index;
    this.name = name;
    this.viewType = viewType;
  }

  public int getIndex() {
    return index;
  }

  public String getName() {
    return name;
  }

  public Supplier<ViewType<?>> getViewType() {
    return viewType;
  }
}
