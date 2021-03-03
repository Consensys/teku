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

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.ssz.backing.TestContainers.TestSubContainer;
import tech.pegasys.teku.ssz.backing.schema.SszVectorSchema;

public class SszVectorTest implements SszCollectionAbstractTest, SszMutableCollectionAbstractTest {

  private final RandomSszDataGenerator randomSsz = new RandomSszDataGenerator();

  @Override
  public Stream<? extends SszData> sszData() {
    return SszCollectionAbstractTest.elementSchemas()
        .flatMap(
            elementSchema ->
                Stream.of(
                    SszVectorSchema.create(elementSchema, 1),
                    SszVectorSchema.create(elementSchema, 2),
                    SszVectorSchema.create(elementSchema, 3),
                    SszVectorSchema.create(elementSchema, 31),
                    SszVectorSchema.create(elementSchema, 32),
                    SszVectorSchema.create(elementSchema, 33)))
        .flatMap(
            vectorSchema ->
                Stream.of(vectorSchema.getDefault(), randomSsz.randomData(vectorSchema)));
  }

  @MethodSource("sszDataArguments")
  @ParameterizedTest
  void size_shouldMatchSchemaLength(SszVector<?> data) {
    assertThat(data.size()).isEqualTo(data.getSchema().getLength());
  }

  @Test
  void testContainerSszSerialize() {
    SszVectorSchema<TestSubContainer, ?> schema =
        SszVectorSchema.create(TestSubContainer.SSZ_SCHEMA, 2);
    assertThat(schema.getSszFixedPartSize()).isEqualTo(80);
  }
}
