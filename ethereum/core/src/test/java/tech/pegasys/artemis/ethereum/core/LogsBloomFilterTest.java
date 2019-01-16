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

package tech.pegasys.artemis.ethereum.core;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import tech.pegasys.artemis.util.bytes.BytesValue;

public class LogsBloomFilterTest {

  @Test
  public void logsBloomFilter() {
    Address address = Address.fromHexString("0x095e7baea6a6c7c4c2dfeb977efac326af552d87");
    BytesValue data = BytesValue.fromHexString("0x0102");
    List<LogTopic> topics = new ArrayList<>();
    topics.add(
        LogTopic.of(
            BytesValue.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000000")));

    Log log = new Log(address, data, topics);
    LogsBloomFilter bloom = LogsBloomFilter.empty();
    bloom.insertLog(log);

    assertEquals(
        BytesValue.fromHexString(
            "0x00000000000000001000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000020000000000000000000800000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000020000000000040000000000000000000000000000000000000000000000000000000"),
        bloom.bytes());
  }
}
