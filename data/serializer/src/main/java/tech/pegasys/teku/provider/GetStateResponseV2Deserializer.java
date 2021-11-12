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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Locale;
import tech.pegasys.teku.api.response.v2.debug.GetStateResponseV2;
import tech.pegasys.teku.api.schema.BeaconState;
import tech.pegasys.teku.api.schema.Version;
import tech.pegasys.teku.api.schema.altair.BeaconStateAltair;
import tech.pegasys.teku.api.schema.phase0.BeaconStatePhase0;

public class GetStateResponseV2Deserializer extends JsonDeserializer<GetStateResponseV2> {
  private final ObjectMapper mapper;

  public GetStateResponseV2Deserializer(final ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public GetStateResponseV2 deserialize(final JsonParser jp, final DeserializationContext ctxt)
      throws IOException {
    JsonNode node = jp.getCodec().readTree(jp);
    final Version version =
        Version.valueOf(node.findValue("version").asText().toLowerCase(Locale.ROOT));
    final BeaconState state;
    switch (version) {
      case altair:
        state = mapper.treeToValue(node.findValue("data"), BeaconStateAltair.class);
        break;
      case phase0:
        state = mapper.treeToValue(node.findValue("data"), BeaconStatePhase0.class);
        break;
      default:
        throw new IOException("Milestone was not able to be decoded");
    }
    return new GetStateResponseV2(version, state);
  }
}
