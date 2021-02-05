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

package tech.pegasys.teku.networking.eth2.gossip.encoding;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.ssz.SSZException;
import tech.pegasys.teku.ssz.backing.ViewRead;
import tech.pegasys.teku.ssz.backing.type.ViewType;

class SszGossipCodec {

  public <T extends ViewRead> Bytes encode(final T value) {
    return value.sszSerialize();
  }

  public <T extends ViewRead> T decode(final Bytes data, final ViewType<T> valueType)
      throws DecodingException {
    try {
      if (!valueType.getSszLengthBounds().isWithinBounds(data.size())) {
        throw new DecodingException(
            "Uncompressed length " + data.size() + " is not within expected bounds");
      }
      final T result = valueType.sszDeserialize(data);
      if (result == null) {
        throw new DecodingException("Unable to decode value");
      }
      return result;
    } catch (SSZException e) {
      throw new DecodingException("Failed to deserialize value", e);
    } catch (Exception e) {
      throw new DecodingException("Encountered exception while deserializing value", e);
    }
  }
}
