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

package tech.pegasys.teku.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.schema.BLSPubKey;
import tech.pegasys.teku.api.schema.BeaconState;
import tech.pegasys.teku.api.schema.ValidatorsRequest;
import tech.pegasys.teku.api.schema.phase0.BeaconStatePhase0;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class JsonProviderTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final JsonProvider jsonProvider = new JsonProvider();
  private static final String Q = "\"";

  @Test
  public void bytes32ShouldSerializeToJsonAndBack() throws JsonProcessingException {
    Bytes32 data = Bytes32.random();
    String serialized = jsonProvider.objectToJSON(data);
    assertEquals(Q + data.toHexString().toLowerCase() + Q, serialized);

    Bytes32 deserialize = jsonProvider.jsonToObject(serialized, Bytes32.class);
    assertEquals(data, deserialize);
  }

  @Test
  public void UInt64ShouldSerializeAndDeserialize() throws JsonProcessingException {
    final UInt64 data = dataStructureUtil.randomUInt64();
    final String serialized = jsonProvider.objectToJSON(data);
    assertEquals(serialized, Q + data.toString() + Q);
    final UInt64 data2 = jsonProvider.jsonToObject(serialized, UInt64.class);
    assertEquals(data2, data);
  }

  @Test
  public void maxUInt64ShouldSerializeAndDeserialize() throws JsonProcessingException {
    final UInt64 data = UInt64.MAX_VALUE;
    final String serialized = jsonProvider.objectToJSON(data);
    assertEquals(serialized, Q + data.toString() + Q);
    final UInt64 data2 = jsonProvider.jsonToObject(serialized, UInt64.class);
    assertEquals(data2, data);
  }

  @Test
  public void UInt256ShouldSerializeAndDeserialize() throws JsonProcessingException {
    final UInt256 data = dataStructureUtil.randomUInt256();
    final String serialized = jsonProvider.objectToJSON(data);
    assertEquals(serialized, Q + data.toShortHexString() + Q);
    final UInt256 data2 = jsonProvider.jsonToObject(serialized, UInt256.class);
    assertEquals(data2, data);
  }

  @Test
  public void maxUInt256ShouldSerializeAndDeserialize() throws JsonProcessingException {
    final UInt256 data = UInt256.MAX_VALUE;
    final String serialized = jsonProvider.objectToJSON(data);
    assertEquals(serialized, Q + data.toShortHexString() + Q);
    final UInt256 data2 = jsonProvider.jsonToObject(serialized, UInt256.class);
    assertEquals(data2, data);
  }

  @Test
  public void UInt64ShouldDeserializeNonHex() throws JsonProcessingException {
    final UInt256 data = jsonProvider.jsonToObject("10", UInt256.class);
    assertEquals(data, UInt256.fromHexString("0xa"));
  }

  @Test
  public void bitListShouldSerializeAndDeserialize() throws JsonProcessingException {
    final int BITLIST_SIZE = 40;
    final SszBitlist data = dataStructureUtil.randomBitlist(BITLIST_SIZE);
    final String asJson = jsonProvider.objectToJSON(data);
    final SszBitlist asData = jsonProvider.jsonToObject(asJson, SszBitlist.class);

    assertThat(data.getAllSetBits()).containsExactlyElementsOf(asData.getAllSetBits());
    assertThat(asData.size()).isEqualTo(BITLIST_SIZE);
  }

  @Test
  public void bitVectorShouldSerializeAndDeserialize() throws JsonProcessingException {
    final int BITVECTOR_SIZE = 40;
    final SszBitvector data = dataStructureUtil.randomSszBitvector(BITVECTOR_SIZE);
    final String asJson = jsonProvider.objectToJSON(data);
    final SszBitvector asData = jsonProvider.jsonToObject(asJson, SszBitvector.class);

    assertThat(data).isEqualTo(asData);
    assertThat(asData.size()).isEqualTo(BITVECTOR_SIZE);
  }

  @Test
  public void doubleShouldSerializeAndDeserialize() throws JsonProcessingException {
    Double fewDecimals = 1.4;
    final String serializedFewDecimals = jsonProvider.objectToJSON(fewDecimals);
    final Double deserializedFewDecimals =
        jsonProvider.jsonToObject(serializedFewDecimals, Double.class);
    assertThat(fewDecimals).isEqualTo(deserializedFewDecimals);

    Double multipleDecimals = 1.41234;
    Double truncatedMultipleDecimals = 1.4123;
    final String serializedMultipleDecimals = jsonProvider.objectToJSON(multipleDecimals);
    final Double deserializedMultipleDecimals =
        jsonProvider.jsonToObject(serializedMultipleDecimals, Double.class);
    assertThat(truncatedMultipleDecimals).isEqualTo(deserializedMultipleDecimals);
  }

  @Test
  public void stringShouldSerializeToJson() throws JsonProcessingException {
    String data = "test";
    assertEquals(Q + data + Q, jsonProvider.objectToJSON(data));
  }

  @Test
  public void byteArrayShouldSerializeToJson() throws JsonProcessingException {
    final byte[] bytes = Bytes.fromHexString("0x00A0F0FF").toArray();
    assertEquals("[\"0\",\"160\",\"240\",\"255\"]", jsonProvider.objectToJSON(bytes));
  }

  @Test
  public void deserializeToBytesShouldHandleSignedBits() throws JsonProcessingException {
    assertThat(jsonProvider.jsonToObject("[\"0\",\"160\",\"240\",\"255\"]", byte[].class))
        .isEqualTo(Bytes.fromHexString("0x00A0F0FF").toArray());
  }

  @Test
  public void deserializeToBytesShouldRejectValuesThatAreTooLarge() {
    assertThatThrownBy(() -> jsonProvider.jsonToObject("[\"256\"]", byte[].class))
        .hasMessage("Expected \"256\" to be a byte value between 0 and 255 inclusive");
  }

  @Test
  public void deserializeToBytesShouldRejectValuesThatAreBelowZero() {
    assertThatThrownBy(() -> jsonProvider.jsonToObject("[\"-999\"]", byte[].class))
        .hasMessage("Expected \"-999\" to be a byte value between 0 and 255 inclusive");
  }

  @Test
  public void deserializeToBytesShouldRejectValuesThatNotNumeric() {
    assertThatThrownBy(() -> jsonProvider.jsonToObject("[\"a\"]", byte[].class))
        .hasMessage("Expected \"a\" to be a byte value between 0 and 255 inclusive");
  }

  @Test
  void beaconStateJsonTest() throws JsonProcessingException {
    tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState stateInternal =
        dataStructureUtil.randomBeaconState(UInt64.valueOf(16));
    BeaconState state = new BeaconStatePhase0(stateInternal);
    String jsonState = jsonProvider.objectToJSON(state);
    assertTrue(jsonState.length() > 0);
  }

  @Test
  void validatorsRequestTest() throws JsonProcessingException {
    final String PUBKEY =
        "0xa99a76ed7796f7be22d5b7e85deeb7c5677e88e511e0b337618f8c4eb61349b4bf2d153f649f7b53359fe8b94a38e44c";
    final String data =
        "{\n"
            + "  \"epoch\": 0, \n"
            + "  \"pubkeys\": [\n"
            + "\""
            + PUBKEY
            + "\"\n"
            + "  ]\n"
            + "}";

    ValidatorsRequest result = jsonProvider.jsonToObject(data, ValidatorsRequest.class);

    assertThat(result.epoch).isEqualTo(UInt64.ZERO);
    assertThat(result.pubkeys).isEqualTo(List.of(BLSPubKey.fromHexString(PUBKEY)));
  }
}
