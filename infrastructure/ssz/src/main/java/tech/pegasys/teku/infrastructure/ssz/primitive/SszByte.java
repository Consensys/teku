/*
 * Copyright Consensys Software Inc., 2026
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

import static com.google.common.base.Preconditions.checkArgument;

import tech.pegasys.teku.infrastructure.ssz.impl.AbstractSszPrimitive;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszPrimitiveSchema;

public class SszByte extends AbstractSszPrimitive<Byte> {

  public static final SszByte ZERO = SszByte.of(0);

  public static SszByte of(final int value) {
    return new SszByte((byte) value);
  }

  public static SszByte asUInt8(final byte value) {
    return new SszByte(value, SszPrimitiveSchemas.UINT8_SCHEMA);
  }

  public static SszByte asUInt8(final int value) {
    checkArgument(value >= 0 && value <= 255, "value must be in uint8 range (0–255)");
    return new SszByte((byte) value, SszPrimitiveSchemas.UINT8_SCHEMA);
  }

  public static SszByte of(final byte value) {
    return new SszByte(value);
  }

  private SszByte(final Byte value) {
    this(value, SszPrimitiveSchemas.BYTE_SCHEMA);
  }

  private SszByte(final Byte value, final AbstractSszPrimitiveSchema<Byte, SszByte> schema) {
    super(value, schema);
  }
}
