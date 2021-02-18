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

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.bls.impl.DeserializeException;
import tech.pegasys.teku.bls.impl.PublicKey;
import tech.pegasys.teku.bls.impl.blst.swig.BLST_ERROR;
import tech.pegasys.teku.bls.impl.blst.swig.blst;
import tech.pegasys.teku.bls.impl.blst.swig.p1;
import tech.pegasys.teku.bls.impl.blst.swig.p1_affine;

public class BlstPublicKey implements PublicKey {
  private static final int COMPRESSED_PK_SIZE = 48;
  private static final int UNCOMPRESSED_PK_LENGTH = 96;

  static final Bytes48 INFINITY_COMPRESSED_BYTES =
      Bytes48.fromHexString(
          "0x"
              + "c0000000000000000000000000000000"
              + "00000000000000000000000000000000"
              + "00000000000000000000000000000000");

  static final BlstPublicKey INFINITY =
      new BlstPublicKey(null) {
        @Override
        public void forceValidation() {}

        @Override
        public Bytes48 toBytesCompressed() {
          return INFINITY_COMPRESSED_BYTES;
        }

        @Override
        public Bytes toBytesUncompressed() {
          throw new UnsupportedOperationException();
        }
      };

  public static BlstPublicKey fromBytesUncompressed(Bytes uncompressed) {
    checkArgument(uncompressed.size() == UNCOMPRESSED_PK_LENGTH);
    p1_affine ecPoint = new p1_affine();
    if (blst.p1_deserialize(ecPoint, uncompressed.toArrayUnsafe()) == BLST_ERROR.BLST_SUCCESS) {
      return new BlstPublicKey(ecPoint);
    } else {
      ecPoint.delete();
      throw new IllegalArgumentException("Invalid PublicKey bytes: " + uncompressed);
    }
  }

  public static BlstPublicKey fromBytes(Bytes48 compressed) {
    if (compressed.equals(INFINITY_COMPRESSED_BYTES)) {
      return INFINITY;
    }
    p1_affine ecPoint = new p1_affine();
    if (blst.p1_uncompress(ecPoint, compressed.toArrayUnsafe()) == BLST_ERROR.BLST_SUCCESS) {
      return new BlstPublicKey(ecPoint);
    } else {
      ecPoint.delete();
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

    List<BlstPublicKey> finitePublicKeys =
        publicKeys.stream().filter(pk -> !pk.isInfinity()).collect(Collectors.toList());
    if (finitePublicKeys.isEmpty()) {
      return INFINITY;
    }
    if (finitePublicKeys.size() < publicKeys.size()) {
      // if the Infinity is not a valid public key then aggregating with any
      // non-valid pubkey should result to a non-valid pubkey
      return INFINITY;
    }

    p1 sum = new p1();
    try {
      blst.p1_from_affine(sum, finitePublicKeys.get(0).ecPoint);
      for (int i = 1; i < finitePublicKeys.size(); i++) {
        blst.p1_add_or_double_affine(sum, sum, finitePublicKeys.get(i).ecPoint);
      }
      p1_affine res = new p1_affine();
      blst.p1_to_affine(res, sum);

      return new BlstPublicKey(res);
    } finally {
      sum.delete();
    }
  }

  final p1_affine ecPoint;

  public BlstPublicKey(p1_affine ecPoint) {
    this.ecPoint = ecPoint;
  }

  @Override
  public void forceValidation() throws IllegalArgumentException {
    if (blst.p1_affine_in_g1(ecPoint) == 0) {
      throw new IllegalArgumentException("Invalid PublicKey: " + toBytesCompressed());
    }
  }

  @Override
  public Bytes48 toBytesCompressed() {
    byte[] res = new byte[COMPRESSED_PK_SIZE];
    blst.p1_affine_compress(res, ecPoint);
    return Bytes48.wrap(res);
  }

  public Bytes toBytesUncompressed() {
    byte[] res = new byte[UNCOMPRESSED_PK_LENGTH];
    blst.p1_affine_serialize(res, ecPoint);
    return Bytes.wrap(res);
  }

  @SuppressWarnings("ReferenceEquality")
  boolean isInfinity() {
    return this == INFINITY;
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
