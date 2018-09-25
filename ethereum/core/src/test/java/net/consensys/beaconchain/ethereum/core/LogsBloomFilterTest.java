package net.consensys.beaconchain.ethereum.core;

import static org.junit.Assert.assertEquals;

import net.consensys.beaconchain.util.bytes.BytesValue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class LogsBloomFilterTest {

  @Test
  public void logsBloomFilter() {
    Address address = Address.fromHexString("0x095e7baea6a6c7c4c2dfeb977efac326af552d87");
    BytesValue data = BytesValue.fromHexString("0x0102");
    List<LogTopic> topics = new ArrayList<>();
    topics.add(LogTopic.of(BytesValue
        .fromHexString("0x0000000000000000000000000000000000000000000000000000000000000000")));

    Log log = new Log(address, data, topics);
    LogsBloomFilter bloom = LogsBloomFilter.empty();
    bloom.insertLog(log);

    assertEquals(
        BytesValue.fromHexString(
            "0x00000000000000001000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000020000000000000000000800000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000020000000000040000000000000000000000000000000000000000000000000000000"),
        bloom.bytes());
  }
}
