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

package tech.pegasys.artemis.datastructures.networking.libp2p.rpc;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.util.SSZTypes.SSZContainer;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class BeaconBlocksMessageResponse implements SimpleOffsetSerializable, SSZContainer {

  private final List<BeaconBlock> blocks;

  public BeaconBlocksMessageResponse(
          List<BeaconBlock> blocks) {
    this.blocks = blocks;
  }

  @Override
  public int getSSZFieldCount() { return 1; }

  @Override
  public List<Bytes> get_fixed_parts() {
    List<Bytes> fixedPartsList =
        new ArrayList<>(
            List.of(
                SSZ.encode(writer -> writer.writeBytes(SimpleOffsetSerializer.serializeVariableCompositeList(blocks)))));
    return fixedPartsList;
  }

  @Override
  public int hashCode() {
    return Objects.hash(blocks);
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof BeaconBlocksMessageResponse)) {
      return false;
    }

    BeaconBlocksMessageResponse other = (BeaconBlocksMessageResponse) obj;
    return Objects.equals(this.blocks(), other.blocks());
  }

  public List<BeaconBlock> blocks() { return blocks; }
}
