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

package tech.pegasys.artemis.network.p2p.jvmlibp2p;

import io.libp2p.core.pubsub.MessageApi;
import io.libp2p.core.pubsub.Topic;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;

public class MockMessageApi implements MessageApi {

  private final ByteBuf data;
  private final byte[] from = new byte[] {0x1, 0x2};
  private final List<Topic> topics;

  public MockMessageApi(final Bytes data, final Topic topic) {
    this.data = Unpooled.wrappedBuffer(data.toArrayUnsafe());
    topics = List.of(topic);
  }

  @Override
  public ByteBuf getData() {
    return data;
  }

  @Override
  public byte[] getFrom() {
    return from;
  }

  @Override
  public long getSeqId() {
    return 1;
  }

  @Override
  public List<Topic> getTopics() {
    return topics;
  }
}
