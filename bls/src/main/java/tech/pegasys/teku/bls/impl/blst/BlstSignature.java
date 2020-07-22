package tech.pegasys.teku.bls.impl.blst;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.impl.PublicKey;
import tech.pegasys.teku.bls.impl.PublicKeyMessagePair;
import tech.pegasys.teku.bls.impl.Signature;
import tech.pegasys.teku.bls.impl.blst.swig.BLST_ERROR;
import tech.pegasys.teku.bls.impl.blst.swig.blst;
import tech.pegasys.teku.bls.impl.blst.swig.p2;
import tech.pegasys.teku.bls.impl.blst.swig.p2_affine;

public class BlstSignature implements Signature {
  private static final int COMPRESSED_SIG_SIZE = 96;

  public static BlstSignature fromBytes(Bytes compressed) {
    checkArgument(
        compressed.size() == COMPRESSED_SIG_SIZE,
        "Expected " + COMPRESSED_SIG_SIZE + " bytes of input but got %s",
        compressed.size());
    p2_affine ec2Point = new p2_affine();
    BLST_ERROR rc = blst.p2_uncompress(ec2Point, compressed.toArrayUnsafe());
    if (rc != BLST_ERROR.BLST_SUCCESS) {
      ec2Point.delete();
      throw new IllegalArgumentException(
          "Invalid Signature bytes: " + compressed + ", error: " + rc);
    } else {
      return new BlstSignature(ec2Point);
    }
  }

  public static BlstSignature aggregate(List<BlstSignature> signatures) {
    p2 sum = new p2();
    blst.p2_from_affine(sum, signatures.get(0).ec2Point);
    for (int i = 1; i < signatures.size(); i++) {
      blst.p2_add_affine(sum, sum, signatures.get(i).ec2Point);
    }
    p2_affine res = new p2_affine();
    blst.p2_to_affine(res, sum);
    sum.delete();
    return new BlstSignature(res);
  }

  final p2_affine ec2Point;

  public BlstSignature(p2_affine ec2Point) {
    this.ec2Point = ec2Point;
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

    List<BlstBatchSemiAggregate> semiAggregates = new ArrayList<>();
    for (int i = 0; i < keysToMessages.size(); i++) {
      BlstPublicKey publicKey = (BlstPublicKey) keysToMessages.get(i).getPublicKey();
      Bytes message = keysToMessages.get(i).getMessage();
      BlstBatchSemiAggregate semiAggregate = BlstBLS12381.INSTANCE
          .prepareBatchVerify(i, Collections.singletonList(publicKey), message, this);
      semiAggregates.add(semiAggregate);
    }

    return BlstBLS12381.INSTANCE.completeBatchVerify(semiAggregates);
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
