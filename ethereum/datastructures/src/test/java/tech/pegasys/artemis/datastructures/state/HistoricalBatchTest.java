/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.datastructures.state;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;

@ExtendWith(BouncyCastleExtension.class)
public class HistoricalBatchTest {

  @Test
  void vectorLengthsTest() {
    List<Integer> vectorLengths =
        List.of(Constants.SLOTS_PER_HISTORICAL_ROOT, Constants.SLOTS_PER_HISTORICAL_ROOT);
    assertEquals(
        vectorLengths,
        SimpleOffsetSerializer.classReflectionInfo.get(HistoricalBatch.class).getVectorLengths());
  }
}
