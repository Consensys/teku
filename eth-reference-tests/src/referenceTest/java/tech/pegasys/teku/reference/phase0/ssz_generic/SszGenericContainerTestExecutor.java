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

import java.io.IOException;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszCollection;
import tech.pegasys.teku.infrastructure.ssz.SszContainer;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteList;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes4;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.reference.TestDataUtils;
import tech.pegasys.teku.reference.phase0.ssz_generic.containers.BitsStructSchema;
import tech.pegasys.teku.reference.phase0.ssz_generic.containers.ComplexTestStructSchema;
import tech.pegasys.teku.reference.phase0.ssz_generic.containers.FixedTestStructSchema;
import tech.pegasys.teku.reference.phase0.ssz_generic.containers.SingleFieldTestStructSchema;
import tech.pegasys.teku.reference.phase0.ssz_generic.containers.SmallTestStructSchema;
import tech.pegasys.teku.reference.phase0.ssz_generic.containers.VarTestStructSchema;

public class SszGenericContainerTestExecutor extends AbstractSszGenericTestExecutor {

  @Override
  protected void assertValueCorrect(final TestDefinition testDefinition, final SszData result)
      throws IOException {
    final Map<String, String> expected = loadExpectedValues(testDefinition);
    final SszContainer container = (SszContainer) result;
    final Map<String, String> actual = formatSszContainer(container);
    assertThat(actual).isEqualTo(expected);
  }

  @SuppressWarnings("unchecked")
  private Map<String, String> loadExpectedValues(final TestDefinition testDefinition)
      throws IOException {
    final Map<String, Object> objectMap =
        TestDataUtils.loadYaml(testDefinition, "value.yaml", Map.class);
    return objectMap.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().toString()));
  }

  @Override
  protected SszSchema<?> getSchema(final TestDefinition testDefinition) {
    final String testName = testDefinition.getTestName();
    final String type = testName.substring(testName.indexOf('/') + 1, testName.indexOf('_'));
    switch (type) {
      case "SingleFieldTestStruct":
        return new SingleFieldTestStructSchema();
      case "BitsStruct":
        return new BitsStructSchema();
      case "SmallTestStruct":
        return new SmallTestStructSchema();
      case "VarTestStruct":
        return new VarTestStructSchema();
      case "FixedTestStruct":
        return new FixedTestStructSchema();
      case "ComplexTestStruct": // Not implemented yet
        return new ComplexTestStructSchema();
      default:
        throw new UnsupportedOperationException("Unsupported container type: " + type);
    }
  }

  @Override
  protected Object parseString(final TestDefinition testDefinition, final String value) {
    return null;
  }

  private Map<String, String> formatSszContainer(final SszContainer container) {
    return container.getSchema().getFieldNames().stream()
        .collect(
            Collectors.toMap(
                Function.identity(),
                name -> format(container.get(container.getSchema().getFieldIndex(name)))));
  }

  private String format(final Object value) {
    if (value instanceof SszByte) {
      return Integer.toString(Byte.toUnsignedInt(((SszByte) value).get()));
    } else if (value instanceof SszBytes4) {
      return Long.toString(
          ((SszBytes4) value).get().getWrappedBytes().toLong(ByteOrder.LITTLE_ENDIAN));
    } else if (value instanceof SszBitlist) {
      return ((SszBitlist) value).sszSerialize().toHexString();
    } else if (value instanceof SszByteList) {
      return ((SszByteList) value).sszSerialize().toHexString();
    } else if (value instanceof SszBitvector) {
      return ((SszBitvector) value).sszSerialize().toHexString();
    } else if (value instanceof SszCollection<?>) {
      final SszCollection<?> list = (SszCollection<?>) value;
      return list.stream().map(this::format).collect(toList()).toString();
    } else if (value instanceof SszContainer) {
      return formatSszContainer((SszContainer) value).toString();
    } else {
      return value.toString();
    }
  }
}
