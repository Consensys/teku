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

package tech.pegasys.artemis.services.adapter.marshall;

import io.grpc.MethodDescriptor.Marshaller;
import io.protostuff.LinkedBuffer;
import io.protostuff.ProtobufIOUtil;
import io.protostuff.Schema;
import io.protostuff.runtime.RuntimeSchema;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import tech.pegasys.artemis.services.adapter.ServiceAdapterException;

/** This class converts a provided Java class into a ProtoBuff schema at runtime */
public class ProtoMarshaller<T> implements Marshaller<T> {

  private Schema<T> schema;

  public ProtoMarshaller(Class<T> clazz) {
    schema = RuntimeSchema.getSchema(clazz);
  }

  @Override
  public InputStream stream(T value) {
    try {
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

      LinkedBuffer linkedBuffer = LinkedBuffer.allocate();
      ProtobufIOUtil.writeTo(outputStream, value, schema, linkedBuffer);

      return new ByteArrayInputStream(outputStream.toByteArray());
    } catch (IOException e) {
      throw new ServiceAdapterException("Error serializing value", e);
    }
  }

  @Override
  public T parse(InputStream stream) {
    try {
      T tmp = schema.newMessage();
      ProtobufIOUtil.mergeFrom(stream, tmp, schema);
      return tmp;
    } catch (IOException e) {
      throw new ServiceAdapterException("Error deserializing value", e);
    }
  }
}
