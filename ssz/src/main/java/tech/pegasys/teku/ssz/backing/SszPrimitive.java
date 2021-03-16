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

package tech.pegasys.teku.ssz.backing;

import tech.pegasys.teku.ssz.backing.schema.SszPrimitiveSchema;

/**
 * A wrapper class for SSZ primitive values. {@link SszPrimitive} classes has no mutable versions
 */
public interface SszPrimitive<ValueType, SszType extends SszPrimitive<ValueType, SszType>>
    extends SszData {

  /** Returns wrapped primitive value */
  ValueType get();

  /** {@link SszPrimitive} classes has no mutable versions */
  @Override
  default SszMutableData createWritableCopy() {
    throw new UnsupportedOperationException("Basic view instances are immutable");
  }

  @Override
  default boolean isWritableSupported() {
    return false;
  }

  @Override
  SszPrimitiveSchema<ValueType, SszType> getSchema();
}
