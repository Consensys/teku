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

package tech.pegasys.teku.ssz;

import static com.google.common.base.Preconditions.checkArgument;

public interface SszMutableCollection<SszElementT extends SszData>
    extends SszMutableComposite<SszElementT> {

  /**
   * Set the same value across the specified indices
   *
   * @param value The value to set
   * @param startIndex The first index in the range to be modified (inclusive)
   * @param endIndex The last index in the range to be modified (exclusive)
   */
  default void setAll(SszElementT value, int startIndex, int endIndex) {
    checkArgument(
        startIndex <= endIndex, "Start index must be less than or equal to the end index");
    for (int i = startIndex; i < endIndex; i++) {
      set(i, value);
    }
  }

  /**
   * Set the same value from index zero (inclusive) up to {@code endIndex} (exclusive).
   *
   * @param value The value to set
   * @param endIndex The index defining the end of the range to set (exclusive)
   */
  default void setAll(SszElementT value, int endIndex) {
    setAll(value, 0, endIndex);
  }
}
