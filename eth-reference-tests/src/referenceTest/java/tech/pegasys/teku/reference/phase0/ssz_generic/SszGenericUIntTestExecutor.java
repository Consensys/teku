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

package tech.pegasys.teku.reference.phase0.ssz_generic;

import static tech.pegasys.teku.reference.phase0.ssz_generic.containers.UInt16PrimitiveSchema.UINT16_SCHEMA;

import java.math.BigInteger;
import org.apache.tuweni.units.bigints.UInt256;
import org.opentest4j.TestAbortedException;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt256;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.reference.phase0.ssz_generic.containers.SszUInt16;

public class SszGenericUIntTestExecutor extends AbstractSszGenericTestExecutor {

  @Override
  protected SszSchema<?> getSchema(final TestDefinition testDefinition) {
    switch (getSize(testDefinition)) {
      case 8:
        return SszPrimitiveSchemas.BYTE_SCHEMA;
      case 16:
        return UINT16_SCHEMA;
      case 64:
        return SszPrimitiveSchemas.UINT64_SCHEMA;
      case 256:
        return SszPrimitiveSchemas.UINT256_SCHEMA;
      case 32:
      case 128:
        throw new TestAbortedException("UInt type not supported: " + testDefinition.getTestName());
      default:
        throw new UnsupportedOperationException(
            "No schema for type: " + testDefinition.getTestName());
    }
  }

  @Override
  protected Object parseString(final TestDefinition testDefinition, final String value) {
    switch (getSize(testDefinition)) {
      case 8:
        return SszByte.of(Integer.parseInt(value));
      case 16:
        return SszUInt16.of(Integer.parseInt(value));
      case 64:
        return SszUInt64.of(UInt64.valueOf(value));
      case 256:
        return SszUInt256.of(UInt256.valueOf(new BigInteger(value)));
      case 32:
      case 128:
        throw new TestAbortedException("UInt type not supported: " + testDefinition.getTestName());
      default:
        throw new UnsupportedOperationException(
            "No parser for type: " + testDefinition.getTestName());
    }
  }
}
