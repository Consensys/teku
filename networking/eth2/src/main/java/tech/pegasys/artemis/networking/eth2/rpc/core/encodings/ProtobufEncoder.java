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

package tech.pegasys.artemis.networking.eth2.rpc.core.encodings;

import com.google.protobuf.CodedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.tuweni.bytes.Bytes;

/**
 * Utilities for encoding protobuf values.
 *
 * @see <a href="https://developers.google.com/protocol-buffers/docs/encoding#varints">Protocol
 *     Buffers</a>
 */
public abstract class ProtobufEncoder {

  public static Bytes encodeVarInt(final int value) {
    try {
      final ByteArrayOutputStream output = new ByteArrayOutputStream();
      final CodedOutputStream codedOutputStream = CodedOutputStream.newInstance(output);
      codedOutputStream.writeUInt32NoTag(value);
      codedOutputStream.flush();
      return Bytes.wrap(output.toByteArray());
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }
}
