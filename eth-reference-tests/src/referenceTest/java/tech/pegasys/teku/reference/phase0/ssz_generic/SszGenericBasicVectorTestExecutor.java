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

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.reference.phase0.ssz_generic.containers.UInt16PrimitiveSchema.UINT16_SCHEMA;

import java.io.IOException;
import java.util.List;
import org.opentest4j.TestAbortedException;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszVector;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszVectorSchema;
import tech.pegasys.teku.reference.TestDataUtils;

public class SszGenericBasicVectorTestExecutor extends AbstractSszGenericTestExecutor {

  @Override
  protected void assertValueCorrect(final TestDefinition testDefinition, final SszData result)
      throws IOException {
    final List<String> expectedValue =
        ((List<?>) TestDataUtils.loadYaml(testDefinition, "value.yaml", List.class))
            .stream().map(value -> parseString(testDefinition, value.toString())).collect(toList());
    final SszVector<?> vector = (SszVector<?>) result;
    final List<?> actualList = vector.stream().map(Object::toString).collect(toList());
    assertThat(actualList).isEqualTo(expectedValue);
  }

  @Override
  protected SszSchema<?> getSchema(final TestDefinition testDefinition) {
    final int vectorLength = getVectorLength(testDefinition);
    final SszSchema<?> elementSchema = getElementSchema(testDefinition);
    return SszVectorSchema.create(elementSchema, vectorLength);
  }

  @Override
  protected String parseString(final TestDefinition testDefinition, final String value) {
    switch (getElementType(testDefinition)) {
      case "bool":
        switch (value) {
          case "false":
            return "0";
          case "true":
            return "1";
          default:
            throw new IllegalArgumentException("Unexpected boolean value: " + value);
        }
      case "uint8":
        // Java will treat the byte as a signed byte so unsigned value to signed byte
        return Byte.toString((byte) Integer.parseUnsignedInt(value));
      default:
        return value;
    }
  }

  // vec_{element type}_{length}
  private SszSchema<?> getElementSchema(final TestDefinition testDefinition) {
    final String elementType = getElementType(testDefinition);
    switch (elementType) {
        // bool is not a bit in this case, it's a full one byte boolean which we don't support
      case "bool":
      case "uint8":
        return SszPrimitiveSchemas.BYTE_SCHEMA;
      case "uint16":
        return UINT16_SCHEMA;
      case "uint64":
        return SszPrimitiveSchemas.UINT64_SCHEMA;
      case "uint256":
        return SszPrimitiveSchemas.UINT256_SCHEMA;
      case "uint32":
      case "uint128":
        throw new TestAbortedException(
            "Element type not supported: "
                + elementType
                + " From: "
                + testDefinition.getTestName());
      default:
        throw new UnsupportedOperationException(
            "No schema for type: " + testDefinition.getTestName());
    }
  }

  private String getElementType(final TestDefinition testDefinition) {
    final String testName = testDefinition.getTestName();
    final String typePart = testName.substring(testName.indexOf('/') + 1 + "vec_".length());
    return typePart.substring(0, typePart.indexOf("_"));
  }

  private int getVectorLength(final TestDefinition testDefinition) {
    final String testName = testDefinition.getTestName();
    final String typePart = testName.substring(testName.indexOf('/') + 1 + "vec_".length());
    final String lengthString = typePart.split("_", -1)[1];
    return Integer.parseInt(lengthString);
  }
}
