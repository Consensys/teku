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

package tech.pegasys.teku.datastructures.networking.libp2p.rpc;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.Bitvector;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;
import tech.pegasys.teku.util.config.Constants;

/** https://github.com/ethereum/eth2.0-specs/blob/v0.11.1/specs/phase0/p2p-interface.md#metadata */
public class MetadataMessage implements RpcRequest, SimpleOffsetSerializable, SSZContainer {

  public static MetadataMessage createDefault() {
    return new MetadataMessage();
  }

  private final UInt64 seqNumber;
  private final Bitvector attnets; // vector of size Constants.ATTESTATION_SUBNET_COUNT

  public MetadataMessage() {
    this(UInt64.ZERO, new Bitvector(Constants.ATTESTATION_SUBNET_COUNT));
  }

  public MetadataMessage(UInt64 seqNumber, Bitvector attnets) {
    checkArgument(attnets.getSize() == Constants.ATTESTATION_SUBNET_COUNT, "Invalid vector size");
    this.seqNumber = seqNumber;
    this.attnets = attnets;
  }

  @Override
  public int getSSZFieldCount() {
    return 2;
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    return List.of(SSZ.encodeUInt64(seqNumber.longValue()), attnets.serialize());
  }

  public UInt64 getSeqNumber() {
    return seqNumber;
  }

  public Bitvector getAttnets() {
    return attnets;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    MetadataMessage that = (MetadataMessage) o;
    return seqNumber.equals(that.seqNumber) && attnets.equals(that.attnets);
  }

  @Override
  public int hashCode() {
    return Objects.hash(seqNumber, attnets);
  }

  @Override
  public String toString() {
    return "MetadataMessage{" + "seqNumber=" + seqNumber + ", attnets=" + attnets + '}';
  }

  @Override
  public int getMaximumRequestChunks() {
    return 1;
  }
}
