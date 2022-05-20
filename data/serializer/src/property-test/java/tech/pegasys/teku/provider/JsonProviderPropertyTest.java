/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.provider;

import static org.apache.commons.lang3.StringEscapeUtils.unescapeJava;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;
import java.util.stream.IntStream;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class JsonProviderPropertyTest {
  private static final String Q = "\"";
  private final JsonProvider jsonProvider = new JsonProvider();

  @Property
  void roundTripBytes32(@ForAll @Size(32) final byte[] value) throws JsonProcessingException {
    Bytes32 data = Bytes32.wrap(value);
    String serialized = jsonProvider.objectToJSON(data);
    assertEquals(Q + data.toHexString().toLowerCase() + Q, serialized);
    Bytes32 deserialize = jsonProvider.jsonToObject(serialized, Bytes32.class);
    assertEquals(data, deserialize);
  }

  @Property
  void roundTripUInt256(@ForAll @Size(32) final byte[] value) throws JsonProcessingException {
    final Bytes bytes = Bytes.wrap(value);
    final UInt256 original = UInt256.fromBytes(bytes);
    final String serialized = jsonProvider.objectToJSON(original);
    assertEquals(serialized, Q + bytes.toQuantityHexString() + Q);
    final UInt256 deserialized = jsonProvider.jsonToObject(serialized, UInt256.class);
    assertEquals(deserialized, original);
  }

  @Property
  void roundTripUInt64(@ForAll final long value) throws JsonProcessingException {
    final UInt64 original = UInt64.fromLongBits(value);
    final String serialized = jsonProvider.objectToJSON(original);
    assertEquals(serialized, Q + original.toString() + Q);
    final UInt64 deserialized = jsonProvider.jsonToObject(serialized, UInt64.class);
    assertEquals(deserialized, original);
  }

  @Property
  void serializeString(@ForAll final String original) throws JsonProcessingException {
    final String serialized = jsonProvider.objectToJSON(original);
    assertThat(unescapeJava(serialized).getBytes(StandardCharsets.UTF_8))
        .isEqualTo((Q + original + Q).getBytes(StandardCharsets.UTF_8));
  }

  @Property
  void roundTripByteArray(@ForAll final byte[] original) throws JsonProcessingException {
    final String serialized = jsonProvider.objectToJSON(original);
    assertEquals(serialized, byteArrayToUnsignedStringWithQuotesAndNoSpaces(original));
    final byte[] deserialized = jsonProvider.jsonToObject(serialized, byte[].class);
    assertThat(deserialized).isEqualTo(original);
  }

  static String byteArrayToUnsignedStringWithQuotesAndNoSpaces(final byte[] bytes) {
    return Arrays.toString(
            Arrays.asList(ArrayUtils.toObject(bytes)).stream()
                .map(Byte::toUnsignedInt)
                .map(s -> Q + s + Q)
                .toArray())
        .replace(" ", "");
  }

  @Property
  public void roundTripBitVector(
      @ForAll final Random random, @ForAll @IntRange(min = 1, max = 1000) final int size)
      throws JsonProcessingException {
    final int[] bits =
        IntStream.range(0, size).sequential().filter(__ -> random.nextBoolean()).toArray();
    final SszBitvector original = SszBitvectorSchema.create(size).ofBits(bits);
    final String serialized = jsonProvider.objectToJSON(original);
    final SszBitvector deserialized = jsonProvider.jsonToObject(serialized, SszBitvector.class);
    assertThat(deserialized).isEqualTo(original);
    assertThat(deserialized.size()).isEqualTo(size);
  }
}
