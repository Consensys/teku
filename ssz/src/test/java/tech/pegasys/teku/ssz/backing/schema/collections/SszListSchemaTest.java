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

package tech.pegasys.teku.ssz.backing.schema.collections;

import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ssz.backing.schema.SszListSchema;
import tech.pegasys.teku.ssz.backing.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.backing.schema.SszSchema;

public class SszListSchemaTest implements SszListSchemaTestBase {

  @Override
  public Stream<? extends SszSchema<?>> testSchemas() {
    return SszListSchemaTestBase.complexListSchemas();
  }

  @Test
  void create_shouldCreateSpecializedSchema() {
    Assertions.assertThat(SszListSchema.create(SszPrimitiveSchemas.BIT_SCHEMA, 10))
        .isInstanceOf(SszBitlistSchema.class);
    Assertions.assertThat(SszListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 10))
        .isInstanceOf(SszUInt64ListSchema.class);
    Assertions.assertThat(SszListSchema.create(SszPrimitiveSchemas.BYTES4_SCHEMA, 10))
        .isInstanceOf(SszPrimitiveListSchema.class);
  }
}
