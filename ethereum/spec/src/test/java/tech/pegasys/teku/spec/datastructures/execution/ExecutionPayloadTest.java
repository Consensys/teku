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
    ExecutionPayloadSchema schema =
        ExecutionPayloadSchema.create(SpecConfig.BYTES_PER_LOGS_BLOOM, 1 << 20, 1 << 14);

    ExecutionPayload executionPayload =
        schema.create(
            b ->
                b.blockHash(Bytes32.random())
                    .parentHash(Bytes32.random())
                    .coinbase(Bytes20.random())
                    .stateRoot(Bytes32.random())
                    .number(randomUInt64())
                    .gasLimit(randomUInt64())
                    .gasUsed(randomUInt64())
                    .timestamp(randomUInt64())
                    .receiptRoot(Bytes32.random())
                    .logsBloom(Bytes.random(SpecConfig.BYTES_PER_LOGS_BLOOM))
                    .transactions(
                        Stream.of(Bytes.random(128), Bytes.random(256), Bytes.random(512))
                            .collect(Collectors.toList())));

    Bytes sszExecutionPayload = executionPayload.sszSerialize();
    ExecutionPayload decodedExecutionPayload = schema.sszDeserialize(sszExecutionPayload);

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
