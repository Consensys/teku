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
import tech.pegasys.teku.ssz.backing.schema.SszCompositeSchemaTestBase;
import tech.pegasys.teku.ssz.backing.schema.SszContainerSchemaTest;
import tech.pegasys.teku.ssz.backing.schema.SszSchema;

public interface SszCollectionSchemaTestBase extends SszCompositeSchemaTestBase {

  static Stream<SszSchema<?>> complexElementSchemas() {
    return Stream.concat(
        SszContainerSchemaTest.testContainerSchemas(),
        Stream.of(
            SszBitvectorSchema.create(1),
            SszBitvectorSchema.create(8),
            SszBitvectorSchema.create(9),
            SszBitlistSchema.create(0),
            SszBitlistSchema.create(1),
            SszBitlistSchema.create(7),
            SszBitlistSchema.create(8),
            SszBitlistSchema.create(9),
            SszByteVectorSchema.create(3),
            SszBytes32VectorSchema.create(3),
            SszUInt64ListSchema.create(3)));
  }
}
