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

package tech.pegasys.artemis.networking.p2p.hobbits.rpc;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.datastructures.operations.Attestation;

@JsonSerialize(using = AttestationMessage.AttestationSerializer.class)
@JsonDeserialize(using = AttestationMessage.AttestationDeserializer.class)
public final class AttestationMessage {

  static class AttestationSerializer extends StdSerializer<AttestationMessage> {

    protected AttestationSerializer() {
      super(AttestationMessage.class);
    }

    @Override
    public void serialize(
        AttestationMessage attestationMessage, JsonGenerator jgen, SerializerProvider provider)
        throws IOException {
      jgen.writeStartObject();
      jgen.writeBinaryField("attestation", attestationMessage.body().toBytes().toArrayUnsafe());
      jgen.writeEndObject();
    }
  }

  static class AttestationDeserializer extends StdDeserializer<AttestationMessage> {

    protected AttestationDeserializer() {
      super(AttestationMessage.class);
    }

    @Override
    public AttestationMessage deserialize(JsonParser jp, DeserializationContext ctxt)
        throws IOException, JsonProcessingException {
      JsonNode node = jp.getCodec().readTree(jp);
      AttestationBody elts = new AttestationBody(Bytes.wrap(node.get("attestation").binaryValue()));
      return new AttestationMessage(elts);
    }
  }

  static class AttestationBody {

    private final Bytes bytes;

    AttestationBody(Bytes bytes) {
      this.bytes = bytes;
    }

    public Bytes bytes() {
      return bytes;
    }
  }

  private final Attestation body;

  AttestationMessage(AttestationBody body) {
    this.body = Attestation.fromBytes(body.bytes());
  }

  @JsonCreator
  public AttestationMessage(@JsonProperty("attestation") Attestation attestation) {
    this.body = attestation;
  }

  @JsonProperty("attestation")
  public Attestation body() {
    return body;
  }
}
