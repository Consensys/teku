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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.schema.BLSPubKey;
import tech.pegasys.teku.api.schema.BeaconState;
import tech.pegasys.teku.api.schema.ValidatorsRequest;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.Bitlist;
import tech.pegasys.teku.ssz.SSZTypes.Bitvector;

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
  public void UInt64ShouldSerializeToJson() throws JsonProcessingException {
    UInt64 data = dataStructureUtil.randomUInt64();
    String serialized = jsonProvider.objectToJSON(data);
    assertEquals(serialized, Q + data.toString() + Q);
  }

  @Test
  public void maxUInt64ShouldSerializeToJson() throws JsonProcessingException {
    UInt64 data = UInt64.MAX_VALUE;
    String serialized = jsonProvider.objectToJSON(data);
    assertEquals(serialized, Q + data.toString() + Q);
  }

  @Test
  public void bitListShouldSerializeAndDeserialize() throws JsonProcessingException {
    final int BITLIST_SIZE = 40;
    final Bitlist data = dataStructureUtil.randomBitlist(BITLIST_SIZE);
    final String asJson = jsonProvider.objectToJSON(data);
    final Bitlist asData = jsonProvider.jsonToObject(asJson, Bitlist.class);

    assertThat(data).isEqualToIgnoringGivenFields(asData, "maxSize");
    assertThat(asData.getCurrentSize()).isEqualTo(BITLIST_SIZE);
  }

  @Test
  public void bitVectorShouldSerializeAndDeserialize() throws JsonProcessingException {
    final int BITVECTOR_SIZE = 40;
    final Bitvector data = dataStructureUtil.randomBitvector(BITVECTOR_SIZE);
    final String asJson = jsonProvider.objectToJSON(data);
    final Bitvector asData = jsonProvider.jsonToObject(asJson, Bitvector.class);

    assertThat(data).isEqualTo(asData);
    assertThat(asData.getSize()).isEqualTo(BITVECTOR_SIZE);
  }

  @Test
  public void stringShouldSerializeToJson() throws JsonProcessingException {
    String data = "test";
    assertEquals(Q + data + Q, jsonProvider.objectToJSON(data));
  }

  @Test
  void beaconStateJsonTest() throws JsonProcessingException {
    tech.pegasys.teku.datastructures.state.BeaconState stateInternal =
        dataStructureUtil.randomBeaconState(UInt64.valueOf(16));
    BeaconState state = new BeaconState(stateInternal);
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
