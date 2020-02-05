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

package tech.pegasys.artemis.networking.eth2.rpc.core;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.annotations.VisibleForTesting;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;

public class MessageBuffer {
  private List<ByteBuf> buffers = new ArrayList<>();
  private Bytes currentData = Bytes.EMPTY;
  private int bytesToTrim = 0;

  public void appendData(final ByteBuf data) {
    data.retain();
    buffers.add(data);
    currentData = Bytes.wrap(currentData, Bytes.wrapByteBuf(data));
  }

  public void consumeData(final DataConsumer dataConsumer) throws RpcException {
    while (!currentData.isEmpty()) {
      final int consumedBytes = dataConsumer.consumeData(currentData);
      checkArgument(
          consumedBytes <= currentData.size(), "Cannot consume more bytes than were in the data");
      checkArgument(consumedBytes >= 0, "Consumed bytes must not be negative");
      if (consumedBytes == 0) {
        // Can't parse any messages, wait for more data to arrive.
        return;
      }

      bytesToTrim += consumedBytes;
      currentData = currentData.slice(consumedBytes);
      trimParsedData();
    }
  }

  private void trimParsedData() {
    for (final Iterator<ByteBuf> i = buffers.iterator(); i.hasNext() && bytesToTrim > 0; ) {
      final ByteBuf buffer = i.next();
      if (buffer.capacity() > bytesToTrim) {
        break;
      }
      bytesToTrim -= buffer.capacity();
      i.remove();
      buffer.release();
    }
  }

  public boolean isEmpty() {
    return currentData.isEmpty();
  }

  @VisibleForTesting
  boolean buffersAreEmpty() {
    return buffers.isEmpty();
  }

  public void close() {
    buffers.forEach(ByteBuf::release);
    buffers.clear();
    currentData = Bytes.EMPTY;
  }

  public interface DataConsumer {
    int consumeData(Bytes currentData) throws RpcException;
  }
}
