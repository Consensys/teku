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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.datastructures.state.Crosslink;
import tech.pegasys.artemis.util.SSZTypes.SSZContainer;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil.SSZTypes;
import tech.pegasys.artemis.util.reflectionInformation.ReflectionInformation;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

public class AttestationData implements SimpleOffsetSerializable, SSZContainer {

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  public static final int SSZ_FIELD_COUNT = 1;

  // LMD GHOST vote
  private Bytes32 beacon_block_root;

  // FFG vote
  private Checkpoint source;
  private Checkpoint target;

  // Crosslink vote
  private Crosslink crosslink;

  public AttestationData(
      Bytes32 beacon_block_root, Checkpoint source, Checkpoint target, Crosslink crosslink) {
    this.beacon_block_root = beacon_block_root;
    this.source = source;
    this.target = target;
    this.crosslink = crosslink;
  }

  public AttestationData(AttestationData attestationData) {
    this.beacon_block_root = attestationData.getBeacon_block_root();
    this.source = new Checkpoint(attestationData.getSource());
    this.target = new Checkpoint(attestationData.getTarget());
    this.crosslink = new Crosslink(attestationData.getCrosslink());
  }

  @Override
  public int getSSZFieldCount() {
    return SSZ_FIELD_COUNT
        + source.getSSZFieldCount()
        + target.getSSZFieldCount()
        + crosslink.getSSZFieldCount();
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    List<Bytes> fixedPartsList = new ArrayList<>();
    fixedPartsList.addAll(List.of(SSZ.encode(writer -> writer.writeFixedBytes(beacon_block_root))));
    fixedPartsList.addAll(source.get_fixed_parts());
    fixedPartsList.addAll(target.get_fixed_parts());
    fixedPartsList.addAll(crosslink.get_fixed_parts());
    return fixedPartsList;
  }

  public static AttestationData fromBytes(Bytes bytes) {
    return SSZ.decode(
        bytes,
        reader ->
            new AttestationData(
                Bytes32.wrap(reader.readFixedBytes(32)),
                Checkpoint.fromBytes(reader.readBytes()),
                Checkpoint.fromBytes(reader.readBytes()),
                Crosslink.fromBytes(reader.readBytes())));
  }

  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeFixedBytes(beacon_block_root);
          writer.writeBytes(source.toBytes());
          writer.writeBytes(target.toBytes());
          writer.writeBytes(crosslink.toBytes());
        });
  }

  @Override
  public int hashCode() {
    return Objects.hash(beacon_block_root, source, target, crosslink);
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof AttestationData)) {
      return false;
    }

    AttestationData other = (AttestationData) obj;
    return Objects.equals(this.getBeacon_block_root(), other.getBeacon_block_root())
        && Objects.equals(this.getSource(), other.getSource())
        && Objects.equals(this.getTarget(), other.getTarget())
        && Objects.equals(this.getCrosslink(), other.getCrosslink());
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public Bytes32 getBeacon_block_root() {
    return beacon_block_root;
  }

  public void setBeacon_block_root(Bytes32 beacon_block_root) {
    this.beacon_block_root = beacon_block_root;
  }

  public Checkpoint getSource() {
    return source;
  }

  public void setSource(Checkpoint source) {
    this.source = source;
  }

  public Checkpoint getTarget() {
    return target;
  }

  public void setTarget(Checkpoint target) {
    this.target = target;
  }

  public Crosslink getCrosslink() {
    return crosslink;
  }

  public void setCrosslink(Crosslink crosslink) {
    this.crosslink = crosslink;
  }

  public Bytes32 hash_tree_root() {
    return HashTreeUtil.merkleize(
        Arrays.asList(
            HashTreeUtil.hash_tree_root(SSZTypes.VECTOR_OF_BASIC, beacon_block_root),
            source.hash_tree_root(),
            target.hash_tree_root(),
            crosslink.hash_tree_root()));
  }
}
