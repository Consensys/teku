/*
 * Copyright ConsenSys Software Inc., 2022
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

import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.ssz.impl.AbstractSszPrimitive;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;

public class SszUInt256 extends AbstractSszPrimitive<UInt256, SszUInt256> {

  public static final SszUInt256 ZERO = SszUInt256.of(UInt256.ZERO);

  public static SszUInt256 of(UInt256 val) {
    return new SszUInt256(val);
  }

  private SszUInt256(UInt256 val) {
    super(val, SszPrimitiveSchemas.UINT256_SCHEMA);
  }
}
