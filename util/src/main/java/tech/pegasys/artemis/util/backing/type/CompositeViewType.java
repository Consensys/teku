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

package tech.pegasys.artemis.util.backing.type;

import tech.pegasys.artemis.util.backing.CompositeView;
import tech.pegasys.artemis.util.backing.Utils;
import tech.pegasys.artemis.util.backing.ViewType;

public interface CompositeViewType<V extends CompositeView> extends ViewType<V> {

  int getMaxLength();

  ViewType<?> getChildType(int index);

  int getBitsPerElement();

  default int getElementsPerChunk() {
    return 256 / getBitsPerElement();
  }

  default int maxChunks() {
    return (getMaxLength() * getBitsPerElement() - 1) / 256 + 1;
  }

  default int treeDepth() {
    return Integer.bitCount(Utils.nextPowerOf2(maxChunks()) - 1);
  }

  default int treeWidth() {
    return Utils.nextPowerOf2(maxChunks());
  }
}
