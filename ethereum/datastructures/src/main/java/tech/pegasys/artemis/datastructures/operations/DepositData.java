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

package tech.pegasys.artemis.datastructures.operations;

import com.google.common.primitives.UnsignedLong;
import java.util.Arrays;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil.SSZTypes;

public class DepositData {

  private long amount;
  private long timestamp;
  private DepositInput deposit_input;

  public DepositData(long amount, long timestamp, DepositInput deposit_input) {
    this.amount = amount;
    this.timestamp = timestamp;
    this.deposit_input = deposit_input;
  }

  public static DepositData fromBytes(Bytes bytes) {
    return SSZ.decode(
        bytes,
        reader ->
            new DepositData(
                reader.readUInt64(),
                reader.readUInt64(),
                DepositInput.fromBytes(reader.readBytes())));
  }

  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeUInt64(amount);
          writer.writeUInt64(timestamp);
          writer.writeBytes(deposit_input.toBytes());
        });
  }

  @Override
  public int hashCode() {
    return Objects.hash(amount, timestamp, deposit_input);
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof DepositData)) {
      return false;
    }

    DepositData other = (DepositData) obj;
    return Objects.equals(this.getAmount(), other.getAmount())
        && Objects.equals(this.getTimestamp(), other.getTimestamp())
        && Objects.equals(this.getDeposit_input(), other.getDeposit_input());
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public DepositInput getDeposit_input() {
    return deposit_input;
  }

  public void setDeposit_input(DepositInput deposit_input) {
    this.deposit_input = deposit_input;
  }

  public long getAmount() {
    return amount;
  }

  public void setAmount(long amount) {
    this.amount = amount;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(long timestamp) {
    this.timestamp = timestamp;
  }

  public Bytes32 hash_tree_root() {
    return HashTreeUtil.merkleize(
        Arrays.asList(
            HashTreeUtil.hash_tree_root(SSZTypes.BASIC, SSZ.encodeUInt64(amount.longValue())),
            HashTreeUtil.hash_tree_root(SSZTypes.BASIC, SSZ.encodeUInt64(timestamp.longValue())),
            deposit_input.hash_tree_root()));
  }
}
