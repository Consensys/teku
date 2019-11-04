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

package org.ethereum.beacon.discovery.format;

import java.util.function.Function;
import org.ethereum.beacon.discovery.type.BytesValue;

// import org.ethereum.beacon.ssz.SSZSerializer;

public class SSZSerializerFactory implements SerializerFactory {

  private final SSZSerializerFactory serializer;

  public SSZSerializerFactory(SSZSerializerFactory serializer) {
    this.serializer = serializer;
  }

  @Override
  public <T> Function<BytesValue, T> getDeserializer(Class<? extends T> objectClass) {
    return bytes -> serializer.getDeserializer(objectClass).apply(bytes);
  }

  @Override
  public <T> Function<T, BytesValue> getSerializer(Class<? extends T> objectClass) {
    return serializer.getSerializer(objectClass);
  }
}
