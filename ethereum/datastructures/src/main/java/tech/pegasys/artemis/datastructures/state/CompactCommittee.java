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

package tech.pegasys.artemis.datastructures.state;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.Copyable;
import tech.pegasys.artemis.util.SSZTypes.SSZContainer;
import tech.pegasys.artemis.util.SSZTypes.SSZList;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;
import tech.pegasys.artemis.util.hashtree.Merkleizable;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

public class CompactCommittee
    implements Copyable<CompactCommittee>, Merkleizable, SimpleOffsetSerializable, SSZContainer {

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  public static final int SSZ_FIELD_COUNT = 2;

  private SSZList<BLSPublicKey> pubkeys; // List bounded by MAX_VALIDATORS_PER_COMMITTEE
  private SSZList<UnsignedLong> compact_validators; // List bounded by MAX_VALIDATORS_PER_COMMITTEE

  public CompactCommittee(SSZList<BLSPublicKey> pubkeys, SSZList<UnsignedLong> compact_validators) {
    this.pubkeys = pubkeys;
    this.compact_validators = compact_validators;
  }

  public CompactCommittee(CompactCommittee compactCommittee) {
    this.pubkeys = new SSZList<>(compactCommittee.getPubkeys());
    this.compact_validators = new SSZList<>(compactCommittee.getCompact_validators());
  }

  public CompactCommittee() {
    this.pubkeys = new SSZList<>(BLSPublicKey.class, Constants.MAX_VALIDATORS_PER_COMMITTEE);
    this.compact_validators =
        new SSZList<>(UnsignedLong.class, Constants.MAX_VALIDATORS_PER_COMMITTEE);
  }

  @Override
  public CompactCommittee copy() {
    return new CompactCommittee(this);
  }

  @Override
  public int getSSZFieldCount() {
    return SSZ_FIELD_COUNT;
  }

  @Override
  public List<Bytes> get_variable_parts() {
    List<Bytes> variablePartsList = new ArrayList<>();
    variablePartsList.addAll(
        List.of(
            SSZ.encode(
                writer ->
                    writer.writeFixedBytesVector(
                        pubkeys.stream()
                            .map(BLSPublicKey::toBytes)
                            .collect(Collectors.toList())))));
    // TODO The below lines are a hack while Tuweni SSZ/SOS is being upgraded.
    variablePartsList.add(
        Bytes.fromHexString(
            compact_validators.stream()
                .map(value -> SSZ.encodeUInt64(value.longValue()).toHexString().substring(2))
                .collect(Collectors.joining())));
    return variablePartsList;
  }

  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeBytesList(
              pubkeys.stream().map(BLSPublicKey::toBytes).collect(Collectors.toList()));
          writer.writeULongIntList(
              64,
              compact_validators.stream()
                  .map(UnsignedLong::longValue)
                  .collect(Collectors.toList()));
        });
  }

  @Override
  public int hashCode() {
    return Objects.hash(pubkeys, compact_validators);
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof CompactCommittee)) {
      return false;
    }

    CompactCommittee other = (CompactCommittee) obj;
    return Objects.equals(this.getCompact_validators(), other.getCompact_validators())
        && Objects.equals(this.getPubkeys(), other.getPubkeys());
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public SSZList<BLSPublicKey> getPubkeys() {
    return pubkeys;
  }

  public void setPubkeys(SSZList<BLSPublicKey> pubkeys) {
    this.pubkeys = pubkeys;
  }

  public SSZList<UnsignedLong> getCompact_validators() {
    return compact_validators;
  }

  public void setCompact_validators(SSZList<UnsignedLong> compact_validators) {
    this.compact_validators = compact_validators;
  }

  @Override
  public Bytes32 hash_tree_root() {
    return HashTreeUtil.merkleize(
        Arrays.asList(
            HashTreeUtil.hash_tree_root_list_pubkey(
                pubkeys, Constants.MAX_VALIDATORS_PER_COMMITTEE),
            HashTreeUtil.hash_tree_root_list_ul(
                Constants.MAX_VALIDATORS_PER_COMMITTEE,
                compact_validators.stream()
                    .map(item -> SSZ.encodeUInt64(item.longValue()))
                    .collect(Collectors.toList()))));
  }
}
