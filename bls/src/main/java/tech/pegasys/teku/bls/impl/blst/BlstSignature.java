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
import supranational.blst.BLST_ERROR;
import supranational.blst.P2;
import supranational.blst.P2_Affine;
import supranational.blst.Pairing;
import tech.pegasys.teku.bls.impl.PublicKey;
import tech.pegasys.teku.bls.impl.PublicKeyMessagePair;
import tech.pegasys.teku.bls.impl.Signature;

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
    P2_Affine ec2Point = new P2_Affine(INFINITY_BYTES.toArrayUnsafe());
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
    try {
      P2_Affine ec2Point = new P2_Affine(compressed.toArrayUnsafe());
      return new BlstSignature(ec2Point, true);
    } catch (Exception e) {
      return new BlstSignature(new P2_Affine(), false);
    }
  }

  static BlstSignature fromSignature(Signature signature) {
    if (signature instanceof BlstSignature) {
      return (BlstSignature) signature;
    } else {
      return fromBytes(signature.toBytesCompressed());
    }
  }

  public static BlstSignature aggregate(List<BlstSignature> signatures) {

    Optional<BlstSignature> invalidSignature =
        signatures.stream().filter(s -> !s.isValid).findFirst();
    if (invalidSignature.isPresent()) {
      throw new IllegalArgumentException(
          "Can't aggregate invalid signature: " + invalidSignature.get());
    }

    P2 sum = new P2();
    for (BlstSignature finiteSignature : signatures) {
      sum.aggregate(finiteSignature.ec2Point);
    }

    return new BlstSignature(sum.to_affine(), true);
  }

  private static void blstPrepareVerifyAggregated(
      BlstPublicKey pubKey, Bytes message, Pairing ctx, BlstSignature blstSignature) {

    BLST_ERROR ret =
        ctx.aggregate(
            pubKey.ecPoint,
            blstSignature == null ? null : blstSignature.ec2Point,
            message.toArrayUnsafe(),
            new byte[0]);
    if (ret != BLST_ERROR.BLST_SUCCESS) {
      throw new IllegalArgumentException("Error: " + ret);
    }
  }

  private static boolean blstCompleteVerifyAggregated(Pairing ctx) {
    ctx.commit();
    return ctx.finalverify();
  }

  final P2_Affine ec2Point;
  private final boolean isValid;

  public BlstSignature(P2_Affine ec2Point, boolean isValid) {
    this.ec2Point = ec2Point;
    this.isValid = isValid;
  }

  @Override
  public Bytes toBytesCompressed() {
    return Bytes.wrap(ec2Point.compress());
  }

  @Override
  public boolean verify(List<PublicKeyMessagePair> keysToMessages) {

    boolean isAnyPublicKeyInfinity =
        keysToMessages.stream()
            .anyMatch(pair -> ((BlstPublicKey) pair.getPublicKey()).isInfinity());
    if (isAnyPublicKeyInfinity) {
      return false;
    }

    Pairing ctx = new Pairing(true, HashToCurve.ETH2_DST);

    for (int i = 0; i < keysToMessages.size(); i++) {
      BlstPublicKey publicKey = BlstPublicKey.fromPublicKey(keysToMessages.get(i).getPublicKey());
      Bytes message = keysToMessages.get(i).getMessage();
      BlstSignature signature = i == 0 ? this : null;
      blstPrepareVerifyAggregated(publicKey, message, ctx, signature);
    }
    return blstCompleteVerifyAggregated(ctx);
  }

  @Override
  public boolean verify(List<PublicKey> publicKeys, Bytes message) {
    return verify(
        BlstPublicKey.aggregate(
            publicKeys.stream().map(BlstPublicKey::fromPublicKey).collect(Collectors.toList())),
        message);
  }

  @Override
  public boolean verify(PublicKey publicKey, Bytes message) {
    return BlstBLS12381.verify(BlstPublicKey.fromPublicKey(publicKey), message, this);
  }

  @Override
  public boolean verify(PublicKey publicKey, Bytes message, String dst) {
    return BlstBLS12381.verify(BlstPublicKey.fromPublicKey(publicKey), message, this, dst);
  }

  @SuppressWarnings("ReferenceEquality")
  @Override
  public boolean isInfinity() {
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
    if (!(o instanceof Signature)) {
      return false;
    }
    return Objects.equals(toBytesCompressed(), ((Signature) o).toBytesCompressed());
  }
}
