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

import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ssz.backing.TestContainers.TestSubContainer;
import tech.pegasys.teku.ssz.backing.schema.SszVectorSchema;

public class SszVectorTest implements SszCollectionAbstractTest {

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
                    SszVectorSchema.create(elementSchema, 4),
                    SszVectorSchema.create(elementSchema, 5),
                    SszVectorSchema.create(elementSchema, 15),
                    SszVectorSchema.create(elementSchema, 16),
                    SszVectorSchema.create(elementSchema, 17),
                    SszVectorSchema.create(elementSchema, 31),
                    SszVectorSchema.create(elementSchema, 32),
                    SszVectorSchema.create(elementSchema, 33)))
        .flatMap(
            vectorSchema ->
                Stream.of(vectorSchema.getDefault(), randomSsz.randomData(vectorSchema)));
  }

  @Test
  void testContainerSszSerialize() {
    SszVectorSchema<TestSubContainer, ?> schema =
        SszVectorSchema.create(TestSubContainer.SSZ_SCHEMA, 2);
    Assertions.assertThat(schema.getSszFixedPartSize()).isEqualTo(80);
  }
}
