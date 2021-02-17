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

package tech.pegasys.teku.ssz.backing;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ssz.backing.TestContainers.TestSubContainer;
import tech.pegasys.teku.ssz.backing.schema.SszVectorSchema;

public class SszVectorTest {

  @Disabled("https://github.com/ConsenSys/teku/issues/3618")
  @Test
  void testContainerSszSerialize() {
    SszVectorSchema<TestSubContainer, ?> schema =
        SszVectorSchema.create(TestSubContainer.SSZ_SCHEMA, 2);
    Assertions.assertThat(schema.getFixedPartSize()).isEqualTo(80);
  }
}
