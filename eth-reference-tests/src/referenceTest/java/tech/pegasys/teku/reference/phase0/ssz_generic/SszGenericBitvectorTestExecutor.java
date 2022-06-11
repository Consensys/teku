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

package tech.pegasys.teku.reference.phase0.ssz_generic;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;

public class SszGenericBitvectorTestExecutor extends AbstractSszGenericTestExecutor {

  @Override
  protected void assertStringValueMatches(
      final TestDefinition testDefinition, final String stringValue, final SszData result) {
    assertThat(result.sszSerialize().toString()).isEqualTo(stringValue);
  }

  @Override
  protected SszSchema<?> getSchema(final TestDefinition testDefinition) {
    return SszBitvectorSchema.create(getSize(testDefinition));
  }

  @Override
  protected Object parseString(final TestDefinition testDefinition, final String value) {
    return value;
  }
}
