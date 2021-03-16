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
import tech.pegasys.teku.ssz.schema.SszCompositeSchemaTestBase;
import tech.pegasys.teku.ssz.schema.SszVectorSchema;

public interface SszVectorSchemaTestBase extends SszCompositeSchemaTestBase {

  static Stream<SszVectorSchema<?, ?>> complexVectorSchemas() {
    return SszCollectionSchemaTestBase.complexElementSchemas()
        .flatMap(
            elementSchema ->
                Stream.of(
                    SszVectorSchema.create(elementSchema, 1),
                    SszVectorSchema.create(elementSchema, 2),
                    SszVectorSchema.create(elementSchema, 3),
                    SszVectorSchema.create(elementSchema, 10)));
  }
}
