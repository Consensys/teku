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

import java.util.Arrays;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;

public class VoluntaryExit {

  private long epoch;
  private long validator_index;
  private BLSSignature signature;

  public VoluntaryExit(long epoch, long validator_index, BLSSignature signature) {
    this.epoch = epoch;
    this.validator_index = validator_index;
    this.signature = signature;
  }

  public static VoluntaryExit fromBytes(Bytes bytes) {
    return SSZ.decode(
        bytes,
        reader ->
            new VoluntaryExit(
                reader.readUInt64(),
                reader.readUInt64(),
                BLSSignature.fromBytes(reader.readBytes())));
  }

  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeUInt64(epoch);
          writer.writeUInt64(validator_index);
          writer.writeBytes(signature.toBytes());
        });
  }

  @Override
  public int hashCode() {
    return Objects.hash(epoch, validator_index, signature);
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof VoluntaryExit)) {
      return false;
    }

    VoluntaryExit other = (VoluntaryExit) obj;
    return Objects.equals(this.getEpoch(), other.getEpoch())
        && Objects.equals(this.getValidator_index(), other.getValidator_index())
        && Objects.equals(this.getSignature(), other.getSignature());
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public long getEpoch() {
    return epoch;
  }

  public void setEpoch(long epoch) {
    this.epoch = epoch;
  }

  public long getValidator_index() {
    return validator_index;
  }

  public void setValidator_index(long validator_index) {
    this.validator_index = validator_index;
  }

  public BLSSignature getSignature() {
    return signature;
  }

  public void setSignature(BLSSignature signature) {
    this.signature = signature;
  }

  public Bytes32 signedRoot(String truncationParam) {
    if (!truncationParam.equals("signature")) {
      throw new UnsupportedOperationException(
          "Only signed_root(proposal, \"signature\") is currently supported for type Proposal.");
    }

    return Bytes32.rightPad(
        HashTreeUtil.merkleHash(
            Arrays.asList(
                HashTreeUtil.hash_tree_root(SSZ.encode(writer -> writer.writeUInt64(epoch))),
                HashTreeUtil.hash_tree_root(
                    SSZ.encode(writer -> writer.writeUInt64(validator_index))))));
  }
}
