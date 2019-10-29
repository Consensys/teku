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

package org.ethereum.beacon.db.source.impl;

import java.util.function.Function;
import tech.pegasys.artemis.util.bytes.ArrayWrappingBytesValue;
import tech.pegasys.artemis.util.bytes.BytesValue;

/** Evaluators of memory footprints for various structures. */
public abstract class MemSizeEvaluators {
  private MemSizeEvaluators() {}

  public static final Function<BytesValue, Long> BytesValueEvaluator =
      value -> {
        if (value instanceof ArrayWrappingBytesValue) {
          // number of bytes + BytesValue header + array header + int offset + int length
          return (long) value.size() + 16 + 32 + 4 + 4;
        } else {
          return (long) value.size() + 16; // number of bytes + BytesValue header
        }
      };
}
