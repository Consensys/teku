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

package tech.pegasys.teku.data.slashinginterchange;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.File;
import java.io.IOException;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.Bytes32Deserializer;
import tech.pegasys.teku.provider.BytesSerializer;

public class YamlProvider {
  private final ObjectMapper objectMapper;

  public YamlProvider() {
    this.objectMapper = new ObjectMapper(new YAMLFactory());
    addTekuMappers();
  }

  private void addTekuMappers() {
    SimpleModule module = new SimpleModule("TekuYaml", new Version(1, 0, 0, null, null, null));
    module.addDeserializer(UInt64.class, new UInt64Deserializer());
    module.addSerializer(UInt64.class, new UInt64Serializer());
    module.addDeserializer(Bytes32.class, new Bytes32Deserializer());
    module.addSerializer(Bytes.class, new BytesSerializer());
    objectMapper.registerModule(module).writer(new DefaultPrettyPrinter());
  }

  public <T> String objectToYaml(T object) throws JsonProcessingException {
    return objectMapper.writeValueAsString(object);
  }

  public <T> T yamlToObject(String json, Class<T> clazz) throws JsonProcessingException {
    return objectMapper.readValue(json, clazz);
  }

  public <T> T fileToObject(File file, Class<T> clazz) throws IOException {
    return objectMapper.readValue(file, clazz);
  }

  public <T> void writeToFile(File file, T object) throws IOException {
    objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, object);
  }

  public ObjectMapper getObjectMapper() {
    return objectMapper;
  }

  public static class UInt64Deserializer extends JsonDeserializer<UInt64> {

    @Override
    public UInt64 deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
      return UInt64.valueOf(p.getValueAsString());
    }
  }

  public static class UInt64Serializer extends JsonSerializer<UInt64> {
    @Override
    public void serialize(UInt64 value, JsonGenerator gen, SerializerProvider serializers)
        throws IOException {
      gen.writeNumber(value.bigIntegerValue());
    }
  }
}
