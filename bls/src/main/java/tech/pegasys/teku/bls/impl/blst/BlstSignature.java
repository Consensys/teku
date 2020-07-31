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
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.impl.PublicKey;
import tech.pegasys.teku.bls.impl.PublicKeyMessagePair;
import tech.pegasys.teku.bls.impl.Signature;
import tech.pegasys.teku.bls.impl.blst.swig.BLST_ERROR;
import tech.pegasys.teku.bls.impl.blst.swig.blst;
import tech.pegasys.teku.bls.impl.blst.swig.p2;
import tech.pegasys.teku.bls.impl.blst.swig.p2_affine;
import tech.pegasys.teku.bls.impl.blst.swig.pairing;

public class BlstSignature implements Signature {
  private static final int COMPRESSED_SIG_SIZE = 96;

  private static final Bytes INFINITY_BYTES =
      Bytes.fromHexString(
          "0x"
              + "c000000000000000000000000000000000000000000000000000000000000000"
              + "0000000000000000000000000000000000000000000000000000000000000000"
              + "0000000000000000000000000000000000000000000000000000000000000000");
  static final BlstSignature INFINITY;

  static {
    p2_affine ec2Point = new p2_affine();
    blst.p2_uncompress(ec2Point, INFINITY_BYTES.toArrayUnsafe());
    INFINITY = new BlstSignature(ec2Point, true);
  }

  public static BlstSignature fromBytes(Bytes compressed) {
    if (compressed.equals(INFINITY_BYTES)) {
      return INFINITY;
    }
    checkArgument(
        compressed.size() == COMPRESSED_SIG_SIZE,
        "Expected " + COMPRESSED_SIG_SIZE + " bytes of input but got %s",
        compressed.size());
    p2_affine ec2Point = new p2_affine();
    try {
      BLST_ERROR rc = blst.p2_uncompress(ec2Point, compressed.toArrayUnsafe());
      return new BlstSignature(ec2Point, rc == BLST_ERROR.BLST_SUCCESS);
    } catch (Exception e) {
      ec2Point.delete();
      throw e;
    }
  }

  public static BlstSignature aggregate(List<BlstSignature> signatures) {
    List<BlstSignature> finiteSignatures =
        signatures.stream().filter(sig -> !sig.isInfinity()).collect(Collectors.toList());

    Optional<BlstSignature> invalidSignature =
        finiteSignatures.stream().filter(s -> !s.isValid).findFirst();
    if (invalidSignature.isPresent()) {
      throw new IllegalArgumentException(
          "Can't aggregate invalid signature: " + invalidSignature.get());
    }

    p2 sum = new p2();
    try {
      blst.p2_from_affine(sum, finiteSignatures.get(0).ec2Point);
      for (int i = 1; i < finiteSignatures.size(); i++) {
        blst.p2_add_affine(sum, sum, finiteSignatures.get(i).ec2Point);
      }
      p2_affine res = new p2_affine();
      blst.p2_to_affine(res, sum);

      return new BlstSignature(res, true);
    } finally {
      sum.delete();
    }
  }

  private static void blstPrepareVerifyAggregated(
      BlstPublicKey pubKey, Bytes message, pairing ctx, BlstSignature blstSignature) {

    p2 g2Hash = HashToCurve.hashToG2(message);
    p2_affine p2Affine = new p2_affine();

    try {
      blst.p2_to_affine(p2Affine, g2Hash);

      BLST_ERROR ret =
          blst.pairing_aggregate_pk_in_g1(
              ctx,
              pubKey.ecPoint,
              blstSignature == null ? null : blstSignature.ec2Point,
              1,
              message.toArrayUnsafe(),
              HashToCurve.ETH2_DST.toArrayUnsafe(),
              null);
      if (ret != BLST_ERROR.BLST_SUCCESS) throw new IllegalArgumentException("Error: " + ret);
    } finally {
      g2Hash.delete();
      p2Affine.delete();
    }
  }

  private static boolean blstCompleteVerifyAggregated(pairing ctx) {
    try {
      blst.pairing_commit(ctx);
      return blst.pairing_finalverify(ctx, null) > 0;
    } finally {
      ctx.delete();
    }
  }

  final p2_affine ec2Point;
  private final boolean isValid;

  public BlstSignature(p2_affine ec2Point, boolean isValid) {
    this.ec2Point = ec2Point;
    this.isValid = isValid;
  }

  @Override
  public Bytes toBytesCompressed() {
    byte[] res = new byte[96];
    blst.p2_affine_compress(res, ec2Point);
    return Bytes.wrap(res);
  }

  @Override
  public Bytes toBytesUncompressed() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean verify(List<PublicKeyMessagePair> keysToMessages) {

    pairing ctx = new pairing();

    try {
      blst.pairing_init(ctx);
      for (int i = 0; i < keysToMessages.size(); i++) {
        BlstPublicKey publicKey = (BlstPublicKey) keysToMessages.get(i).getPublicKey();
        Bytes message = keysToMessages.get(i).getMessage();
        BlstSignature signature = i == 0 ? this : null;
        blstPrepareVerifyAggregated(publicKey, message, ctx, signature);
      }
      return blstCompleteVerifyAggregated(ctx);
    } finally {
      ctx.delete();
    }
  }

  @Override
  public boolean verify(List<PublicKey> publicKeys, Bytes message) {
    return verify(
        BlstPublicKey.aggregate(
            publicKeys.stream().map(k -> (BlstPublicKey) k).collect(Collectors.toList())),
        message);
  }

  @Override
  public boolean verify(PublicKey publicKey, Bytes message) {
    return BlstBLS12381.verify((BlstPublicKey) publicKey, message, this);
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
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    BlstSignature that = (BlstSignature) o;
    return Objects.equals(toBytesCompressed(), that.toBytesCompressed());
  }
}
