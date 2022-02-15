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

package tech.pegasys.teku.ethereum.executionlayer.client.serialization;

import static com.google.common.base.Preconditions.checkArgument;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import org.apache.tuweni.units.bigints.UInt256;

public class UInt256AsHexDeserializer extends JsonDeserializer<UInt256> {

  @Override
  public UInt256 deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    final String hexValue = p.getValueAsString();
    checkArgument(hexValue.startsWith("0x"), "Hex value must start with 0x");
    return UInt256.fromHexString(hexValue);
  }
}
