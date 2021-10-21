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

package tech.pegasys.teku.spec.datastructures.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class ExecutionPayloadHeaderTest {

  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createMinimalMerge());

  @Test
  public void shouldSszEncodeAndDecode() {
    ExecutionPayloadHeader executionPayloadHeader =
        new ExecutionPayloadHeader(
            Bytes32.random(),
            dataStructureUtil.randomBytes20(),
            Bytes32.random(),
            Bytes32.random(),
            Bytes.random(SpecConfig.BYTES_PER_LOGS_BLOOM),
            Bytes32.random(),
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomUInt64(),
            Bytes.random(SpecConfig.MAX_EXTRA_DATA_BYTES),
            UInt256.fromBytes(Bytes32.random()),
            Bytes32.random(),
            Bytes32.random());

    Bytes sszExecutionPayloadHeader = executionPayloadHeader.sszSerialize();
    ExecutionPayloadHeader decodedExecutionPayloadHeader =
        ExecutionPayloadHeader.SSZ_SCHEMA.sszDeserialize(sszExecutionPayloadHeader);

    assertEquals(executionPayloadHeader, decodedExecutionPayloadHeader);
  }
}
