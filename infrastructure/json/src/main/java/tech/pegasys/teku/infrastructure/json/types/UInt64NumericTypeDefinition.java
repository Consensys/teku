/*
 * Copyright Consensys Software Inc., 2025
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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import java.io.IOException;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class UInt64NumericTypeDefinition extends PrimitiveTypeDefinition<UInt64> {

  @Override
  public void serializeOpenApiTypeFields(final JsonGenerator gen) throws IOException {
    gen.writeStringField("type", "number");
  }

  @Override
  public void serialize(final UInt64 value, final JsonGenerator gen) throws IOException {
    gen.writeNumber(value.longValue());
  }

  @Override
  public UInt64 deserialize(final JsonParser parser) throws IOException {
    return UInt64.valueOf(parser.getLongValue());
  }

  @Override
  public String serializeToString(final UInt64 value) {
    return value.toString();
  }

  @Override
  public UInt64 deserializeFromString(final String value) {
    return UInt64.valueOf(value);
  }
}

