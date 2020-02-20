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

package tech.pegasys.artemis.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.primitives.UnsignedLong;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.util.SSZTypes.SSZList;
import tech.pegasys.artemis.util.SSZTypes.SSZVector;

class JsonProviderTest {
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
  public void unsignedLongShouldSerializeToJson() throws JsonProcessingException {
    UnsignedLong data = DataStructureUtil.randomUnsignedLong(1111);
    String serialized = jsonProvider.objectToJSON(data);
    assertEquals(serialized, data.toString());
  }

  @Test
  public void vectorShouldSerializeToJson() throws JsonProcessingException {
    SSZVector<String> data = new SSZVector<String>(List.of("One", "Two"), String.class);
    String serialized = jsonProvider.objectToJSON(data);
    assertEquals(serialized, "[" + Q + "One" + Q + "," + Q + "Two" + Q + "]");
  }

  @Test
  public void vectorOfUnsignedLongShouldSerializeToJson() throws JsonProcessingException {
    SSZVector<UnsignedLong> data =
        new SSZVector<>(List.of(UnsignedLong.ONE, UnsignedLong.MAX_VALUE), UnsignedLong.class);
    String serialized = jsonProvider.objectToJSON(data);
    assertEquals(serialized, "[1,18446744073709551615]");
  }

  @Test
  public void sszlistOfUnsignedLongShouldSerializeToJson() throws JsonProcessingException {
    SSZList<UnsignedLong> data =
        new SSZList<>(List.of(UnsignedLong.ONE, UnsignedLong.MAX_VALUE), 3, UnsignedLong.class);
    String serialized = jsonProvider.objectToJSON(data);
    assertEquals(serialized, "[1,18446744073709551615]");
  }

  @Test
  public void stringShouldSerializeToJson() throws JsonProcessingException {
    String data = "test";
    assertEquals(Q + data + Q, jsonProvider.objectToJSON(data));
  }

  @Test
  void beaconStateJsonTest() throws JsonProcessingException {
    BeaconState state = DataStructureUtil.randomBeaconState(UnsignedLong.valueOf(16), 100);
    String jsonState = jsonProvider.objectToJSON(state);
    assertTrue(jsonState.length() > 0);
  }
}
