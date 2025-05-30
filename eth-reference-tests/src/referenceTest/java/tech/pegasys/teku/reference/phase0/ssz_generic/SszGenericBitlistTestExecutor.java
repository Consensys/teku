/*
 * Copyright Consensys Software Inc., 2025
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
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitlistSchema;

public class SszGenericBitlistTestExecutor extends AbstractSszGenericTestExecutor {

  @Override
  protected void assertStringValueMatches(
      final TestDefinition testDefinition, final String stringValue, final SszData result) {
    assertThat(result.sszSerialize().toString()).isEqualTo(stringValue);
  }

  @Override
  protected SszBitlistSchema<SszBitlist> getSchema(final TestDefinition testDefinition) {
    return SszBitlistSchema.create(getSize(testDefinition));
  }

  @Override
  protected Object parseString(final TestDefinition testDefinition, final String value) {
    return value;
  }
}
