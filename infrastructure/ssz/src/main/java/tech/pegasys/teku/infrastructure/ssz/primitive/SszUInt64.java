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

package tech.pegasys.teku.infrastructure.ssz.primitive;

import tech.pegasys.teku.infrastructure.ssz.impl.AbstractSszPrimitive;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SszUInt64 extends AbstractSszPrimitive<UInt64, SszUInt64> {

  public static final SszUInt64 ZERO = SszUInt64.of(UInt64.ZERO);

  public static SszUInt64 of(UInt64 val) {
    return new SszUInt64(val);
  }

  private SszUInt64(UInt64 val) {
    super(val, SszPrimitiveSchemas.UINT64_SCHEMA);
  }

  public long longValue() {
    return get().longValue();
  }
}
