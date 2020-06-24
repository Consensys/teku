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

package tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.Compressor.Decompressor;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.noop.NoopCompressor;

public class NoopCompressorTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final Compressor compressor = new NoopCompressor();

  @Test
  public void roundTrip() throws Exception {
    final BeaconState state = dataStructureUtil.randomBeaconState(0);
    final Bytes serializedState =
        Bytes.wrap(SimpleOffsetSerializer.serialize(state).toArrayUnsafe());

    final Bytes compressed = compressor.compress(serializedState);
    Decompressor decompressor = compressor.createDecompressor(serializedState.size());
    Optional<ByteBuf> uncompressed =
        decompressor.decodeOneMessage(Unpooled.wrappedBuffer(compressed.toArray()));
    assertThat(uncompressed).isPresent();
    assertThat(Bytes.wrapByteBuf(uncompressed.get())).isEqualTo(serializedState);
  }
}
