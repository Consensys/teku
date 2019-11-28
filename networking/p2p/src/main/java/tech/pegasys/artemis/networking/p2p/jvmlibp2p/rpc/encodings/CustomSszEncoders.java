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

package tech.pegasys.artemis.networking.p2p.jvmlibp2p.rpc.encodings;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.BeaconBlocksByRootRequestMessage;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

public class CustomSszEncoders {

  private static final ImmutableMap<Class<?>, Function<SimpleOffsetSerializable, Bytes>>
      CUSTOM_SERIALIZERS =
          ImmutableMap.of(
              BeaconBlocksByRootRequestMessage.class,
              message -> encodeBlockByRootMessage((BeaconBlocksByRootRequestMessage) message));

  private static final ImmutableMap<Class<?>, Function<Bytes, ?>> CUSTOM_DESERIALIZERS =
      ImmutableMap.of(
          BeaconBlocksByRootRequestMessage.class, CustomSszEncoders::decodeBlockByRootMessage);

  @SuppressWarnings("unchecked")
  public static <T extends SimpleOffsetSerializable> Function<T, Bytes> getEncoder(final T data) {
    return (Function<T, Bytes>)
        CUSTOM_SERIALIZERS.getOrDefault(data.getClass(), SimpleOffsetSerializer::serialize);
  }

  @SuppressWarnings("unchecked")
  public static <T> Function<Bytes, T> getDecoder(final Class<T> clazz) {
    return (Function<Bytes, T>)
        CUSTOM_DESERIALIZERS.getOrDefault(
            clazz, data -> SimpleOffsetSerializer.deserialize(data, clazz));
  }

  private static Bytes encodeBlockByRootMessage(final BeaconBlocksByRootRequestMessage message) {
    return SSZ.encode(writer -> writer.writeFixedBytesVector(message.getBlockRoots()));
  }

  private static BeaconBlocksByRootRequestMessage decodeBlockByRootMessage(final Bytes message) {
    checkArgument(message.size() % Bytes32.SIZE == 0, "Cannot split message into Bytes32 chunks");
    final List<Bytes32> blockRoots = new ArrayList<>();
    for (int i = 0; i < message.size(); i += Bytes32.SIZE) {
      blockRoots.add(Bytes32.wrap(message.slice(i, Bytes32.SIZE)));
    }
    return new BeaconBlocksByRootRequestMessage(blockRoots);
  }
}
