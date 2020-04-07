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

package tech.pegasys.artemis.datastructures.operations;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.bls.BLSPublicKey;
import tech.pegasys.artemis.ssz.SSZTypes.SSZContainer;
import tech.pegasys.artemis.ssz.sos.SimpleOffsetSerializable;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil.SSZTypes;
import tech.pegasys.artemis.util.hashtree.Merkleizable;

public class DepositMessage implements SimpleOffsetSerializable, SSZContainer, Merkleizable {

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  private static final int SSZ_FIELD_COUNT = 2;

  private final BLSPublicKey pubkey;
  private final Bytes32 withdrawal_credentials;
  private final UnsignedLong amount;

  public DepositMessage(
      final BLSPublicKey pubkey, final Bytes32 withdrawal_credentials, final UnsignedLong amount) {
    this.pubkey = pubkey;
    this.withdrawal_credentials = withdrawal_credentials;
    this.amount = amount;
  }

  public BLSPublicKey getPubkey() {
    return pubkey;
  }

  public Bytes32 getWithdrawal_credentials() {
    return withdrawal_credentials;
  }

  public UnsignedLong getAmount() {
    return amount;
  }

  @Override
  public int getSSZFieldCount() {
    return pubkey.getSSZFieldCount() + SSZ_FIELD_COUNT;
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    List<Bytes> fixedPartsList = new ArrayList<>();
    fixedPartsList.addAll(pubkey.get_fixed_parts());
    fixedPartsList.addAll(
        List.of(
            SSZ.encode(writer -> writer.writeFixedBytes(withdrawal_credentials)),
            SSZ.encodeUInt64(amount.longValue())));
    return fixedPartsList;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DepositMessage that = (DepositMessage) o;
    return Objects.equals(pubkey, that.pubkey)
        && Objects.equals(withdrawal_credentials, that.withdrawal_credentials)
        && Objects.equals(amount, that.amount);
  }

  @Override
  public int hashCode() {
    return Objects.hash(pubkey, withdrawal_credentials, amount);
  }

  @Override
  public Bytes32 hash_tree_root() {
    return HashTreeUtil.merkleize(
        Arrays.asList(
            HashTreeUtil.hash_tree_root(SSZTypes.VECTOR_OF_BASIC, pubkey.toBytes()),
            HashTreeUtil.hash_tree_root(SSZTypes.VECTOR_OF_BASIC, withdrawal_credentials),
            HashTreeUtil.hash_tree_root(SSZTypes.BASIC, SSZ.encodeUInt64(amount.longValue()))));
  }
}
