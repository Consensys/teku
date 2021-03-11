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

package tech.pegasys.teku.bls.impl.blst;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes48;
import supranational.blst.P1;
import supranational.blst.P1_Affine;
import tech.pegasys.teku.bls.impl.DeserializeException;
import tech.pegasys.teku.bls.impl.PublicKey;

class BlstPublicKey implements PublicKey {
  private static final Bytes48 INFINITY_COMPRESSED_BYTES =
      Bytes48.fromHexString(
          "0x"
              + "c0000000000000000000000000000000"
              + "00000000000000000000000000000000"
              + "00000000000000000000000000000000");

  public static BlstPublicKey fromBytes(Bytes48 compressed) {
    try {
      P1_Affine ecPoint = new P1_Affine(compressed.toArrayUnsafe());
      return new BlstPublicKey(ecPoint);
    } catch (Exception err) {
      throw new DeserializeException("Invalid PublicKey bytes: " + compressed);
    }
  }

  static BlstPublicKey fromPublicKey(PublicKey publicKey) {
    if (publicKey instanceof BlstPublicKey) {
      return (BlstPublicKey) publicKey;
    } else {
      return fromBytes(publicKey.toBytesCompressed());
    }
  }

  public static BlstPublicKey aggregate(List<BlstPublicKey> publicKeys) {
    checkArgument(publicKeys.size() > 0);

    Optional<BlstPublicKey> maybeInfinitePubkey =
        publicKeys.stream().filter(BlstPublicKey::isInfinity).findAny();
    if (maybeInfinitePubkey.isPresent()) {
      // if the Infinity is not a valid public key then aggregating with any
      // non-valid pubkey should result to a non-valid pubkey
      return maybeInfinitePubkey.get();
    }

    P1 sum = new P1();
    for (BlstPublicKey publicKey : publicKeys) {
      sum.aggregate(publicKey.ecPoint);
    }

    return new BlstPublicKey(sum.to_affine());
  }

  final P1_Affine ecPoint;
  private final Supplier<Boolean> isInfinity =
      Suppliers.memoize(() -> toBytesCompressed().equals(INFINITY_COMPRESSED_BYTES));

  public BlstPublicKey(P1_Affine ecPoint) {
    this.ecPoint = ecPoint;
  }

  @Override
  public void forceValidation() throws IllegalArgumentException {

    if (!ecPoint.in_group()) {
      throw new IllegalArgumentException("Invalid PublicKey: " + toBytesCompressed());
    }
  }

  @Override
  public Bytes48 toBytesCompressed() {
    return Bytes48.wrap(ecPoint.compress());
  }

  boolean isInfinity() {
    return isInfinity.get();
  }

  @Override
  public int hashCode() {
    return toBytesCompressed().hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof PublicKey)) {
      return false;
    }

    return Objects.equals(toBytesCompressed(), ((PublicKey) o).toBytesCompressed());
  }

  @Override
  public String toString() {
    return toBytesCompressed().toHexString();
  }
}
