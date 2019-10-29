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

package org.ethereum.beacon.discovery.storage;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.ethereum.beacon.chain.storage.impl.SerializerFactory;
import org.ethereum.beacon.discovery.NodeRecordInfo;
import org.ethereum.beacon.discovery.enr.NodeRecordFactory;
import tech.pegasys.artemis.util.bytes.BytesValue;

public class NodeSerializerFactory implements SerializerFactory {
  private final Map<Class, Function<BytesValue, Object>> deserializerMap = new HashMap<>();
  private final Map<Class, Function<Object, BytesValue>> serializerMap = new HashMap<>();

  public NodeSerializerFactory(NodeRecordFactory nodeRecordFactory) {
    deserializerMap.put(
        NodeRecordInfo.class, bytes1 -> NodeRecordInfo.fromRlpBytes(bytes1, nodeRecordFactory));
    serializerMap.put(NodeRecordInfo.class, o -> ((NodeRecordInfo) o).toRlpBytes());
    deserializerMap.put(NodeIndex.class, NodeIndex::fromRlpBytes);
    serializerMap.put(NodeIndex.class, o -> ((NodeIndex) o).toRlpBytes());
    deserializerMap.put(
        NodeBucket.class, bytes -> NodeBucket.fromRlpBytes(bytes, nodeRecordFactory));
    serializerMap.put(NodeBucket.class, o -> ((NodeBucket) o).toRlpBytes());
  }

  @Override
  public <T> Function<BytesValue, T> getDeserializer(Class<? extends T> objectClass) {
    if (!deserializerMap.containsKey(objectClass)) {
      throw new RuntimeException(String.format("Type %s is not supported", objectClass));
    }
    return bytes -> (T) deserializerMap.get(objectClass).apply(bytes);
  }

  @Override
  public <T> Function<T, BytesValue> getSerializer(Class<? extends T> objectClass) {
    if (!serializerMap.containsKey(objectClass)) {
      throw new RuntimeException(String.format("Type %s is not supported", objectClass));
    }
    return value -> serializerMap.get(objectClass).apply(value);
  }
}
