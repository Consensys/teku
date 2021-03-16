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

package tech.pegasys.teku.ssz;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.ssz.schema.collections.SszVectorSchemaTestBase;

public class SszVectorTest
    implements SszCollectionTestBase, SszMutableCollectionTestBase, SszMutableRefCompositeTestBase {

  private final RandomSszDataGenerator randomSsz = new RandomSszDataGenerator();

  @Override
  public Stream<SszVector<?>> sszData() {
    return SszVectorSchemaTestBase.complexVectorSchemas()
        .flatMap(
            vectorSchema ->
                Stream.of(vectorSchema.getDefault(), randomSsz.randomData(vectorSchema)));
  }

  @MethodSource("sszDataArguments")
  @ParameterizedTest
  void size_shouldMatchSchemaLength(SszVector<?> data) {
    assertThat(data.size()).isEqualTo(data.getSchema().getLength());
  }
}
