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

package tech.pegasys.teku.infrastructure.json.types;

import static com.google.common.base.Preconditions.checkArgument;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import java.io.IOException;

class ByteTypeDefinition extends PrimitiveTypeDefinition<Byte> {

  @Override
  public void serializeOpenApiTypeFields(final JsonGenerator gen) throws IOException {
    gen.writeStringField("type", "string");
    gen.writeStringField("format", "byte");
  }

  @Override
  public void serialize(final Byte value, final JsonGenerator gen) throws IOException {
    gen.writeString(Integer.toString(Byte.toUnsignedInt(value)));
  }

  @Override
  public Byte deserialize(final JsonParser parser) throws IOException {
    final int value = Integer.parseUnsignedInt(parser.getValueAsString(), 10);
    checkArgument(
        value <= Math.pow(2, Byte.SIZE), "Value %s exceeds maximum for unsigned byte", value);
    return (byte) value;
  }
}
