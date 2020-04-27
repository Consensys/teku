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

package tech.pegasys.teku.datastructures.state;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.ssz.SSZTypes.SSZMutableVector;
import tech.pegasys.teku.ssz.SSZTypes.SSZVector;
import tech.pegasys.teku.util.config.Constants;

@ExtendWith(BouncyCastleExtension.class)
public class HistoricalBatchTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();

  @Test
  void vectorLengthsTest() {
    List<Integer> vectorLengths =
        List.of(Constants.SLOTS_PER_HISTORICAL_ROOT, Constants.SLOTS_PER_HISTORICAL_ROOT);
    assertEquals(
        vectorLengths,
        SimpleOffsetSerializer.classReflectionInfo.get(HistoricalBatch.class).getVectorLengths());
  }

  @Test
  void roundTripViaSsz() {
    SSZMutableVector<Bytes32> block_roots =
        SSZVector.createMutable(Constants.SLOTS_PER_HISTORICAL_ROOT, Bytes32.ZERO);
    SSZMutableVector<Bytes32> state_roots =
        SSZVector.createMutable(Constants.SLOTS_PER_HISTORICAL_ROOT, Bytes32.ZERO);
    IntStream.range(0, Constants.SLOTS_PER_HISTORICAL_ROOT)
        .forEach(
            i -> {
              block_roots.set(i, dataStructureUtil.randomBytes32());
              state_roots.set(i, dataStructureUtil.randomBytes32());
            });
    HistoricalBatch batch = new HistoricalBatch(block_roots, state_roots);
    Bytes serialized = SimpleOffsetSerializer.serialize(batch);
    HistoricalBatch result = SimpleOffsetSerializer.deserialize(serialized, HistoricalBatch.class);
    assertEquals(batch, result);
  }
}
