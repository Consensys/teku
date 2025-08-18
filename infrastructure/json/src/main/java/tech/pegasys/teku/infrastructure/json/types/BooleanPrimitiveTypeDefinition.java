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

/** Boolean for {@link tech.pegasys.teku.infrastructure.ssz.primitive.SszBoolean} */
class BooleanPrimitiveTypeDefinition extends PrimitiveTypeDefinition<Boolean> {
  @Override
  public void serializeOpenApiTypeFields(final JsonGenerator gen) throws IOException {
    gen.writeStringField("type", "boolean");
  }

  @Override
  public void serialize(final Boolean value, final JsonGenerator gen) throws IOException {
    gen.writeBoolean(value);
  }

  @Override
  public Boolean deserialize(final JsonParser parser) throws IOException {
    return parser.getValueAsBoolean();
  }

  @Override
  public String serializeToString(final Boolean value) {
    return value.toString();
  }

  @Override
  public Boolean deserializeFromString(final String valueAsString) {
    return Boolean.valueOf(valueAsString);
  }
}
