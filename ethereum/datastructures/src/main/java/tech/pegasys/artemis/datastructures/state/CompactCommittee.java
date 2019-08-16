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
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.Copyable;
import tech.pegasys.artemis.util.SSZTypes.SSZContainer;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil.SSZTypes;
import tech.pegasys.artemis.util.hashtree.Merkleizable;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

public class CompactCommittee
    implements Copyable<CompactCommittee>, Merkleizable, SimpleOffsetSerializable, SSZContainer {

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  public static final int SSZ_FIELD_COUNT = 2;

  private List<BLSPublicKey> pubkeys; // List bounded by MAX_VALIDATORS_PER_COMMITTEE
  private List<UnsignedLong> compact_validators; // List bounded by MAX_VALIDATORS_PER_COMMITTEE

  public CompactCommittee(List<BLSPublicKey> pubkeys, List<UnsignedLong> compact_validators) {
    this.pubkeys = pubkeys;
    this.compact_validators = compact_validators;
  }

  public CompactCommittee(CompactCommittee compactCommittee) {
    this.pubkeys = compactCommittee.getPubkeys().stream().collect(Collectors.toList());
    this.compact_validators =
        compactCommittee.getCompact_validators().stream().collect(Collectors.toList());
  }

  public CompactCommittee() {
    this.pubkeys = new ArrayList<>();
    this.compact_validators = new ArrayList<>();
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
    variablePartsList.addAll(
        compact_validators.stream()
            .map(value -> SSZ.encodeUInt64(value.longValue()))
            .collect(Collectors.toList()));
    variablePartsList.addAll(List.of(Bytes.EMPTY, Bytes.EMPTY));
    return variablePartsList;
  }

  public static CompactCommittee fromBytes(Bytes bytes) {
    return SSZ.decode(
        bytes,
        reader ->
            new CompactCommittee(
                reader.readBytesList().stream()
                    .map(BLSPublicKey::fromBytes)
                    .collect(Collectors.toList()),
                reader.readUInt64List().stream()
                    .map(UnsignedLong::fromLongBits)
                    .collect(Collectors.toList())));
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

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public List<BLSPublicKey> getPubkeys() {
    return pubkeys;
  }

  public void setPubkeys(List<BLSPublicKey> pubkeys) {
    this.pubkeys = pubkeys;
  }

  public List<UnsignedLong> getCompact_validators() {
    return compact_validators;
  }

  public void setCompact_validators(List<UnsignedLong> compact_validators) {
    this.compact_validators = compact_validators;
  }

  @Override
  public Bytes32 hash_tree_root() {
    return HashTreeUtil.merkleize(
        Arrays.asList(
            HashTreeUtil.hash_tree_root(
                SSZTypes.VECTOR_OF_BASIC,
                pubkeys.stream()
                    .map(BLSPublicKey::toBytes)
                    .map(item -> SSZ.encodeBytes(item))
                    .collect(Collectors.toList())
                    .toArray(new Bytes[0])),
            HashTreeUtil.hash_tree_root_list_ul(
                Constants.MAX_VALIDATORS_PER_COMMITTEE,
                compact_validators.stream()
                    .map(item -> SSZ.encodeUInt64(item.longValue()))
                    .collect(Collectors.toList()))));
  }
}
