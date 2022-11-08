/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.infrastructure.ssz;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszContainerSchema.NamedSchema;

class SszTypeGeneratorTest {

  @Test
  void shouldDefineASimpleType() throws Exception {
    final SszTypeGenerator<SimpleType, SimpleTypeSchema> sszTypeGenerator =
        new SszTypeGenerator<>(
            SimpleType.class,
            SimpleTypeSchema.class,
            NamedSchema.of("field1", SszPrimitiveSchemas.UINT64_SCHEMA, SszUInt64.class));
    final SimpleTypeSchema schema = sszTypeGenerator.defineType();
    assertThat(schema.getField1Schema()).isSameAs(SszPrimitiveSchemas.UINT64_SCHEMA);
    final SimpleType simpleType = schema.create(SszUInt64.ZERO);
    assertThat(simpleType).isInstanceOf(SimpleType.class);
    assertThat(simpleType.getField1()).isEqualTo(SszUInt64.ZERO);
  }
}
