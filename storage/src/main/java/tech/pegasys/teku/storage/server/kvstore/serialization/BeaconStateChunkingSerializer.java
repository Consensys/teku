/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.storage.server.kvstore.serialization;

import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.ssz.sos.SszByteArrayChunksWriter;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

class BeaconStateChunkingSerializer implements KvStoreChunkingSerializer<BeaconState> {

  private final Spec spec;

  BeaconStateChunkingSerializer(final Spec spec) {
    this.spec = spec;
  }

  @Override
  public BeaconState deserialize(final List<byte[]> data) {
    return spec.deserializeBeaconState(Bytes.wrap(data.stream().map(Bytes::wrap).toList()));
  }

  @Override
  public List<byte[]> serialize(final BeaconState value) {
    final SszByteArrayChunksWriter sszByteArrayChunksWriter =
        new SszByteArrayChunksWriter(
            value.getBeaconStateSchema().getSszSize(value.getBackingNode()), 1024_000);
    value.sszSerialize(sszByteArrayChunksWriter);
    return sszByteArrayChunksWriter.getChunks();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final BeaconStateChunkingSerializer that = (BeaconStateChunkingSerializer) o;
    return Objects.equals(spec, that.spec);
  }

  @Override
  public int hashCode() {
    return Objects.hash(spec);
  }
}
