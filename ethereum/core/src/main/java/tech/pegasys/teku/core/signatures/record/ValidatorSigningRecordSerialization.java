/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.core.signatures.record;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.NumericNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.primitives.UnsignedLong;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.apache.tuweni.bytes.Bytes;

class ValidatorSigningRecordSerialization {

  private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory());
  private static final String BLOCK_SLOT_FIELD_NAME = "lastSignedBlockSlot";
  private static final String SOURCE_EPOCH_FIELD_NAME = "lastSignedAttestationSourceEpoch";
  private static final String TARGET_EPOCH_FIELD_NAME = "lastSignedAttestationTargetEpoch";

  static {
    MAPPER.registerModule(
        new SimpleModule()
            .addSerializer(ValidatorSigningRecord.class, new ValidatorSigningRecordSerializer())
            .addDeserializer(
                ValidatorSigningRecord.class, new ValidatorSigningRecordDeserializer()));
  }

  static ValidatorSigningRecord readRecord(final Bytes data) {
    try {
      return MAPPER.readerFor(ValidatorSigningRecord.class).readValue(data.toArrayUnsafe());
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static Bytes writeRecord(final ValidatorSigningRecord record) {
    try (final ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      MAPPER.writerFor(ValidatorSigningRecord.class).writeValue(out, record);
      return Bytes.wrap(out.toByteArray());
    } catch (JsonGenerationException | JsonMappingException e) {
      throw new IllegalStateException("Failed to serialize ValidatorSigningRecord", e);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static class ValidatorSigningRecordSerializer
      extends JsonSerializer<ValidatorSigningRecord> {

    @Override
    public void serialize(
        final ValidatorSigningRecord value,
        final JsonGenerator gen,
        final SerializerProvider serializers)
        throws IOException {
      gen.writeStartObject();
      writeUnsignedLong(gen, BLOCK_SLOT_FIELD_NAME, value.getBlockSlot());
      writeUnsignedLong(gen, SOURCE_EPOCH_FIELD_NAME, value.getAttestationSourceEpoch());
      writeUnsignedLong(gen, TARGET_EPOCH_FIELD_NAME, value.getAttestationTargetEpoch());
      gen.writeEndObject();
    }

    private void writeUnsignedLong(
        final JsonGenerator gen, final String fieldName, final UnsignedLong value)
        throws IOException {
      if (value.equals(UnsignedLong.MAX_VALUE)) {
        gen.writeNullField(fieldName);
      } else {
        gen.writeNumberField(fieldName, value.bigIntegerValue());
      }
    }
  }

  private static class ValidatorSigningRecordDeserializer
      extends JsonDeserializer<ValidatorSigningRecord> {
    @Override
    public ValidatorSigningRecord deserialize(final JsonParser p, final DeserializationContext ctxt)
        throws IOException {
      final TreeNode node = p.getCodec().readTree(p);
      final UnsignedLong blockSlot = getUnsignedLong(node, BLOCK_SLOT_FIELD_NAME);
      final UnsignedLong attestationSourceEpoch = getUnsignedLong(node, SOURCE_EPOCH_FIELD_NAME);
      final UnsignedLong attestationTargetEpoch = getUnsignedLong(node, TARGET_EPOCH_FIELD_NAME);
      return new ValidatorSigningRecord(blockSlot, attestationSourceEpoch, attestationTargetEpoch);
    }

    private UnsignedLong getUnsignedLong(final TreeNode node, final String fieldName) {
      final TreeNode valueNode = node.get(fieldName);
      if (valueNode instanceof NullNode) {
        return UnsignedLong.MAX_VALUE;
      }
      return UnsignedLong.valueOf(((NumericNode) valueNode).bigIntegerValue());
    }
  }
}
