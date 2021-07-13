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

package tech.pegasys.teku.provider;

import static com.google.common.base.Preconditions.checkArgument;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;

public class ByteArrayDeserializer extends JsonDeserializer<byte[]> {

  @Override
  public byte[] deserialize(final JsonParser p, final DeserializationContext ctxt)
      throws IOException, JsonProcessingException {
    ObjectCodec oc = p.getCodec();
    JsonNode node = oc.readTree(p);
    checkArgument(node.isArray(), "Expected array but did not appear to be one!");
    if (node.size() == 0) {
      return null;
    }
    byte[] data = new byte[node.size()];
    for (int i = 0; i < node.size(); i++) {
      final Integer current = node.get(i).asInt(-1);
      checkArgument(
          current >= 0 && current <= 255,
          "Expected %s to be a byte value between 0 and 255 inclusive",
          node.get(i));
      data[i] = current.byteValue();
    }
    return data;
  }
}
