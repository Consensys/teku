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

package tech.pegasys.teku.datastructures.blocks;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.teku.pow.event.DepositsFromBlockEvent;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;

public class Eth1BlockData implements SimpleOffsetSerializable, SSZContainer {
  public static final int SSZ_FIELD_COUNT = 3;

  private final UnsignedLong depositCount;
  private final UnsignedLong lastDepositIndex;
  private final Bytes32 blockHash;

  public Eth1BlockData(
      final UnsignedLong depositCount,
      final UnsignedLong lastDepositIndex,
      final Bytes32 blockHash) {
    this.lastDepositIndex = lastDepositIndex;
    this.blockHash = blockHash;
    this.depositCount = depositCount;
  }

  public Eth1BlockData(final DepositsFromBlockEvent event) {
    this.depositCount = UnsignedLong.valueOf(event.getDeposits().size());
    this.lastDepositIndex = event.getLastDepositIndex();
    this.blockHash = event.getBlockHash();
  }

  public Bytes32 getBlockHash() {
    return blockHash;
  }

  public UnsignedLong getDepositCount() {
    return depositCount;
  }

  @Override
  public int getSSZFieldCount() {
    return SSZ_FIELD_COUNT;
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    return List.of(
        SSZ.encodeUInt64(getDepositCount().longValue()),
        SSZ.encodeUInt64(getLastDepositIndex().longValue()),
        SSZ.encode(writer -> writer.writeFixedBytes(getBlockHash())));
  }

  public UnsignedLong getLastDepositIndex() {
    return lastDepositIndex;
  }
}
