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

package tech.pegasys.teku.reference.phase0.ssz_generic;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.opentest4j.TestAbortedException;
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
import tech.pegasys.teku.reference.phase0.ssz_generic.containers.ProgressiveTestStructSchema;
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
    return switch (type) {
      case "SingleFieldTestStruct" -> new SingleFieldTestStructSchema();
      case "BitsStruct" -> new BitsStructSchema();
      case "SmallTestStruct" -> new SmallTestStructSchema();
      case "VarTestStruct" -> new VarTestStructSchema();
      case "FixedTestStruct" -> new FixedTestStructSchema();
      case "ComplexTestStruct" -> // Not implemented yet
          new ComplexTestStructSchema();
      case "ProgressiveTestStruct" -> new ProgressiveTestStructSchema();
      case "ProgressiveBitsStruct" ->
          throw new TestAbortedException(
              "ProgressiveBitsStruct type not supported: " + testDefinition.getTestName());
      default -> throw new UnsupportedOperationException("Unsupported container type: " + type);
    };
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
    return switch (value) {
      case SszByte sszByte -> Integer.toString(Byte.toUnsignedInt(sszByte.get()));
      case SszBytes4 sszBytes4 ->
          Long.toString(sszBytes4.get().getWrappedBytes().toLong(ByteOrder.LITTLE_ENDIAN));
      case SszBitlist sszBits -> sszBits.sszSerialize().toHexString();
      case SszByteList sszBytes -> sszBytes.sszSerialize().toHexString();
      case SszBitvector bitvector -> bitvector.sszSerialize().toHexString();
      case SszCollection<?> list -> list.stream().map(this::format).toList().toString();
      case SszContainer sszContainer -> formatSszContainer(sszContainer).toString();
      default -> Objects.toString(value, null);
    };
  }
}
