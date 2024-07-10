/*
 * Copyright Consensys Software Inc., 2024
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

import java.util.stream.Stream;
import tech.pegasys.teku.infrastructure.ssz.RandomSszDataGenerator.StableContainerMode;
import tech.pegasys.teku.infrastructure.ssz.schema.SszStableContainerSchemaTest;

public class SszStableContainerTest extends AbstractSszStableContainerBaseTest {

  @Override
  public Stream<SszContainer> sszData() {
    RandomSszDataGenerator smallListsRandomSCGen = new RandomSszDataGenerator().withMaxListSize(1);
    RandomSszDataGenerator largeListsRandomSCGen =
        new RandomSszDataGenerator()
            .withStableContainerMode(StableContainerMode.RANDOM)
            .withMaxListSize(1024);
    RandomSszDataGenerator emptySCGen =
        new RandomSszDataGenerator().withStableContainerMode(StableContainerMode.EMPTY);
    RandomSszDataGenerator fullSCGen =
        new RandomSszDataGenerator().withStableContainerMode(StableContainerMode.FULL);

    RandomSszDataGenerator anotherRound =
        new RandomSszDataGenerator().withStableContainerMode(StableContainerMode.RANDOM);
    return SszStableContainerSchemaTest.testContainerSchemas()
        .flatMap(
            schema ->
                Stream.of(
                    schema.getDefault(),
                    smallListsRandomSCGen.randomData(schema),
                    largeListsRandomSCGen.randomData(schema),
                    emptySCGen.randomData(schema),
                    fullSCGen.randomData(schema),
                    // more combinations of active fields
                    anotherRound.randomData(schema),
                    anotherRound.randomData(schema)));
  }
}
