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
import tech.pegasys.teku.bls.impl.PublicKey;
import tech.pegasys.teku.bls.impl.PublicKeyMessagePair;
import tech.pegasys.teku.bls.impl.Signature;
import tech.pegasys.teku.bls.impl.blst.swig.BLST_ERROR;
import tech.pegasys.teku.bls.impl.blst.swig.P2;
import tech.pegasys.teku.bls.impl.blst.swig.P2_Affine;
import tech.pegasys.teku.bls.impl.blst.swig.Pairing;

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
    INFINITY = new BlstSignature(ec2Point);
  }

  public static BlstSignature fromBytes(Bytes compressed) {
    if (compressed.equals(INFINITY_BYTES)) {
      return INFINITY;
    }
    checkArgument(
        compressed.size() == COMPRESSED_SIG_SIZE,
        "Expected " + COMPRESSED_SIG_SIZE + " bytes of input but got %s",
        compressed.size());
    P2_Affine ec2Point = null;
    try {
      ec2Point = new P2_Affine(compressed.toArrayUnsafe());
      return new BlstSignature(ec2Point);
    } catch (RuntimeException e) {
      if (ec2Point != null) {
        ec2Point.delete();
      }
      throw new IllegalArgumentException(e);
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
    List<BlstSignature> finiteSignatures =
        signatures.stream().filter(sig -> !sig.isInfinity()).collect(Collectors.toList());

    P2 sum = new P2();
    try {
      for (BlstSignature finiteSignature : finiteSignatures) {
        sum.aggregate(finiteSignature.ec2Point);
      }

      return new BlstSignature(sum.to_affine());
    } finally {
      sum.delete();
    }
  }

  private static void blstPrepareVerifyAggregated(
      BlstPublicKey pubKey, Bytes message, Pairing ctx, BlstSignature blstSignature) {

    BLST_ERROR ret =
        ctx.aggregate(
            pubKey.ecPoint,
            blstSignature == null ? null : blstSignature.ec2Point,
            message.toArrayUnsafe(),
            new byte[0]);
    if (ret != BLST_ERROR.BLST_SUCCESS) throw new IllegalArgumentException("Error: " + ret);
  }

  private static boolean blstCompleteVerifyAggregated(Pairing ctx) {
    try {
      ctx.commit();
      return ctx.finalverify();
    } finally {
      ctx.delete();
    }
  }

  final P2_Affine ec2Point;

  public BlstSignature(P2_Affine ec2Point) {
    this.ec2Point = ec2Point;
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

    try {
      for (int i = 0; i < keysToMessages.size(); i++) {
        BlstPublicKey publicKey = BlstPublicKey.fromPublicKey(keysToMessages.get(i).getPublicKey());
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
    if (!(o instanceof Signature)) {
      return false;
    }
    return Objects.equals(toBytesCompressed(), ((Signature) o).toBytesCompressed());
  }
}
