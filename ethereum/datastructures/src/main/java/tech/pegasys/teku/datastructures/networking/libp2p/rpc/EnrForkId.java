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

package tech.pegasys.teku.datastructures.networking.libp2p.rpc;

import com.google.common.base.MoreObjects;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;

public class EnrForkId implements SimpleOffsetSerializable, SSZContainer {

  public static final int SSZ_FIELD_COUNT = 3;

  private final Bytes4 forkDigest;
  private final Bytes4 nextForkVersion;
  private final UInt64 nextForkEpoch;

  public EnrForkId(
      final Bytes4 forkDigest, final Bytes4 nextForkVersion, final UInt64 nextForkEpoch) {
    this.forkDigest = forkDigest;
    this.nextForkVersion = nextForkVersion;
    this.nextForkEpoch = nextForkEpoch;
  }

  public Bytes4 getForkDigest() {
    return forkDigest;
  }

  public Bytes4 getNextForkVersion() {
    return nextForkVersion;
  }

  public UInt64 getNextForkEpoch() {
    return nextForkEpoch;
  }

  @Override
  public int getSSZFieldCount() {
    return SSZ_FIELD_COUNT;
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    return List.of(
        SSZ.encode(writer -> writer.writeFixedBytes(forkDigest.getWrappedBytes())),
        SSZ.encode(writer -> writer.writeFixedBytes(nextForkVersion.getWrappedBytes())),
        SSZ.encodeUInt64(nextForkEpoch.longValue()));
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final EnrForkId enrForkID = (EnrForkId) o;
    return Objects.equals(forkDigest, enrForkID.forkDigest)
        && Objects.equals(nextForkVersion, enrForkID.nextForkVersion)
        && Objects.equals(nextForkEpoch, enrForkID.nextForkEpoch);
  }

  @Override
  public int hashCode() {
    return Objects.hash(forkDigest, nextForkVersion, nextForkEpoch);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("forkDigest", forkDigest)
        .add("nextForkVersion", nextForkVersion)
        .add("nextForkEpoch", nextForkEpoch)
        .toString();
  }
}
