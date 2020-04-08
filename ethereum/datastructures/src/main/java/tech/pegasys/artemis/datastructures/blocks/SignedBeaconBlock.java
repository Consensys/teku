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

package tech.pegasys.artemis.datastructures.blocks;

import com.google.common.base.MoreObjects;
import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.bls.BLSSignature;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.artemis.ssz.SSZTypes.SSZContainer;
import tech.pegasys.artemis.ssz.sos.SimpleOffsetSerializable;

public class SignedBeaconBlock implements SimpleOffsetSerializable, SSZContainer {

  private final BeaconBlock message;
  private final BLSSignature signature;

  public SignedBeaconBlock(final BeaconBlock message, final BLSSignature signature) {
    this.message = message;
    this.signature = signature;
  }

  public BeaconBlock getMessage() {
    return message;
  }

  public BLSSignature getSignature() {
    return signature;
  }

  @Override
  public int getSSZFieldCount() {
    return message.getSSZFieldCount() + signature.getSSZFieldCount();
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    final List<Bytes> parts = new ArrayList<>();
    parts.add(Bytes.EMPTY);
    parts.addAll(signature.get_fixed_parts());
    return parts;
  }

  @Override
  public List<Bytes> get_variable_parts() {
    return List.of(SimpleOffsetSerializer.serialize(message), Bytes.EMPTY);
  }

  public UnsignedLong getSlot() {
    return message.getSlot();
  }

  public Bytes32 getParent_root() {
    return message.getParent_root();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SignedBeaconBlock that = (SignedBeaconBlock) o;
    return Objects.equals(message, that.message) && Objects.equals(signature, that.signature);
  }

  @Override
  public int hashCode() {
    return Objects.hash(message, signature);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("message", message)
        .add("signature", signature)
        .toString();
  }
}
