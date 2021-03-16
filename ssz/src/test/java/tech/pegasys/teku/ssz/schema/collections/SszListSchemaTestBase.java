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

package tech.pegasys.teku.ssz.schema.collections;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.ssz.schema.SszListSchema;

public interface SszListSchemaTestBase extends SszCollectionSchemaTestBase {

  static Stream<SszListSchema<?, ?>> complexListSchemas() {
    return SszCollectionSchemaTestBase.complexElementSchemas()
        .flatMap(
            elementSchema ->
                Stream.of(
                    SszListSchema.create(elementSchema, 0),
                    SszListSchema.create(elementSchema, 1),
                    SszListSchema.create(elementSchema, 2),
                    SszListSchema.create(elementSchema, 3),
                    SszListSchema.create(elementSchema, 10),
                    SszListSchema.create(elementSchema, 1L << 33)));
  }

  @MethodSource("testSchemaArguments")
  @ParameterizedTest
  default void getChildSchema_shouldThrowIndexOutOfBounds(SszListSchema<?, ?> schema) {}
}
