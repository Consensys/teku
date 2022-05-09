/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.spec.executionlayer;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.eth1.Eth1Address;

public class PayloadAttributes {
  private final UInt64 timestamp;
  private final Bytes32 prevRandao;
  private final Eth1Address feeRecipient;

  public PayloadAttributes(UInt64 timestamp, Bytes32 prevRandao, Eth1Address feeRecipient) {
    this.timestamp = timestamp;
    this.prevRandao = prevRandao;
    this.feeRecipient = feeRecipient;
  }

  public UInt64 getTimestamp() {
    return timestamp;
  }

  public Bytes32 getPrevRandao() {
    return prevRandao;
  }

  public Eth1Address getFeeRecipient() {
    return feeRecipient;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final PayloadAttributes that = (PayloadAttributes) o;
    return Objects.equals(timestamp, that.timestamp)
        && Objects.equals(prevRandao, that.prevRandao)
        && Objects.equals(feeRecipient, that.feeRecipient);
  }

  @Override
  public int hashCode() {
    return Objects.hash(timestamp, prevRandao, feeRecipient);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("timestamp", timestamp)
        .add("prevRandao", prevRandao)
        .add("feeRecipient", feeRecipient)
        .toString();
  }
}
