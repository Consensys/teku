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

package tech.pegasys.teku.spec.executionengine;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.type.Bytes20;

public class PayloadAttributes {
  private final UInt64 timestamp;
  private final Bytes32 random;
  private final Bytes20 feeRecipient;

  public PayloadAttributes(UInt64 timestamp, Bytes32 random, Bytes20 feeRecipient) {
    this.timestamp = timestamp;
    this.random = random;
    this.feeRecipient = feeRecipient;
  }

  public UInt64 getTimestamp() {
    return timestamp;
  }

  public Bytes32 getRandom() {
    return random;
  }

  public Bytes20 getFeeRecipient() {
    return feeRecipient;
  }
}
