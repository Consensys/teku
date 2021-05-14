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

import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.ssz.type.Bytes20;

public class ExecutionPayloadTest {

  @Test
  public void shouldSszEncodeAndDecode() {
    ExecutionPayload executionPayload =
        new ExecutionPayload(
            Bytes32.random(),
            Bytes32.random(),
            Bytes20.random(),
            Bytes32.random(),
            randomUInt64(),
            randomUInt64(),
            randomUInt64(),
            randomUInt64(),
            Bytes32.random(),
            Bytes.random(SpecConfig.BYTES_PER_LOGS_BLOOM),
            Stream.of(Bytes.random(128), Bytes.random(256), Bytes.random(512))
                .collect(Collectors.toList()));

    Bytes sszExecutionPayload = executionPayload.sszSerialize();
    ExecutionPayload decodedExecutionPayload =
        ExecutionPayload.SSZ_SCHEMA.sszDeserialize(sszExecutionPayload);

    assertEquals(executionPayload, decodedExecutionPayload);
  }

  private int seed = 92892824;

  private UInt64 randomUInt64() {
    return UInt64.fromLongBits(new Random(nextSeed()).nextLong());
  }

  private int nextSeed() {
    return seed++;
  }
}
