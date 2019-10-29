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

package org.ethereum.beacon.core.operations;

import com.google.common.base.Objects;
import org.ethereum.beacon.core.types.BLSPubkey;
import org.ethereum.beacon.core.types.BLSSignature;
import org.ethereum.beacon.core.types.Gwei;
import org.ethereum.beacon.core.types.SlotNumber;
import org.ethereum.beacon.core.types.ValidatorIndex;
import org.ethereum.beacon.ssz.annotation.SSZ;
import org.ethereum.beacon.ssz.annotation.SSZSerializable;

/**
 * A value transfer between validators.
 *
 * @see <a
 *     href="https://github.com/ethereum/eth2.0-specs/blob/0.4.0/specs/core/0_beacon-chain.md#transfers">Transfers</a>
 *     in the spec.
 */
@SSZSerializable
public class Transfer {
  /** Sender index. */
  @SSZ private final ValidatorIndex sender;
  /** Recipient index. */
  @SSZ private final ValidatorIndex recipient;
  /** Amount in Gwei. */
  @SSZ private final Gwei amount;
  /** Fee in Gwei for block proposer. */
  @SSZ private final Gwei fee;
  /** Inclusion slot. */
  @SSZ private final SlotNumber slot;
  /** Sender withdrawal pubkey. */
  @SSZ private final BLSPubkey pubkey;
  /** Sender signature. */
  @SSZ private final BLSSignature signature;

  public Transfer(
      ValidatorIndex sender,
      ValidatorIndex recipient,
      Gwei amount,
      Gwei fee,
      SlotNumber slot,
      BLSPubkey pubkey,
      BLSSignature signature) {
    this.sender = sender;
    this.recipient = recipient;
    this.amount = amount;
    this.fee = fee;
    this.slot = slot;
    this.pubkey = pubkey;
    this.signature = signature;
  }

  public ValidatorIndex getSender() {
    return sender;
  }

  public ValidatorIndex getRecipient() {
    return recipient;
  }

  public Gwei getAmount() {
    return amount;
  }

  public Gwei getFee() {
    return fee;
  }

  public SlotNumber getSlot() {
    return slot;
  }

  public BLSPubkey getPubkey() {
    return pubkey;
  }

  public BLSSignature getSignature() {
    return signature;
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) {
      return true;
    }
    if (object == null || getClass() != object.getClass()) {
      return false;
    }
    Transfer transfer = (Transfer) object;
    return Objects.equal(sender, transfer.sender)
        && Objects.equal(recipient, transfer.recipient)
        && Objects.equal(amount, transfer.amount)
        && Objects.equal(fee, transfer.fee)
        && Objects.equal(slot, transfer.slot)
        && Objects.equal(pubkey, transfer.pubkey)
        && Objects.equal(signature, transfer.signature);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(sender, recipient, amount, fee, slot, pubkey, signature);
  }
}
