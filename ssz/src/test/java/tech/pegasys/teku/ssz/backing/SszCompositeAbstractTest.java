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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.ssz.backing.schema.SszCompositeSchema;
import tech.pegasys.teku.ssz.backing.schema.SszSchema;

public interface SszCompositeAbstractTest extends SszDataAbstractTest {

  @MethodSource("sszDataArguments")
  @ParameterizedTest
  default void get_childSchemaMatches(SszComposite<?> data) {
    SszCompositeSchema<?> schema = data.getSchema();
    for (int i = 0; i < data.size(); i++) {
      SszData child = data.get(i);
      assertThat(child).isNotNull();
      SszSchema<?> childSchema = child.getSchema();
      assertThat(childSchema).isNotNull();
      assertThat(childSchema).isEqualTo(schema.getChildSchema(i));
    }
  }

  @MethodSource("sszDataArguments")
  @ParameterizedTest
  default void get_throwsOutOfBounds(SszComposite<?> data) {
    assertThatThrownBy(() -> data.get(-1)).isInstanceOf(IndexOutOfBoundsException.class);
    assertThatThrownBy(() -> data.get(data.size())).isInstanceOf(IndexOutOfBoundsException.class);
    assertThatThrownBy(
            () -> data.get((int) Long.min(Integer.MAX_VALUE, data.getSchema().getMaxLength())))
        .isInstanceOf(IndexOutOfBoundsException.class);
  }
}
