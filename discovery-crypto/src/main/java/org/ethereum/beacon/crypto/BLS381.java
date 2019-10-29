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

package org.ethereum.beacon.crypto;

import static com.google.common.base.Preconditions.checkArgument;
import static org.ethereum.beacon.crypto.bls.milagro.MilagroCodecs.G1;
import static org.ethereum.beacon.crypto.bls.milagro.MilagroCodecs.G2;
import static org.ethereum.beacon.crypto.bls.milagro.MilagroParameters.ORDER;

import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.util.List;
import org.apache.milagro.amcl.BLS381.BIG;
import org.apache.milagro.amcl.BLS381.ECP;
import org.apache.milagro.amcl.BLS381.ECP2;
import org.apache.milagro.amcl.BLS381.FP12;
import org.apache.milagro.amcl.BLS381.PAIR;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.util.BigIntegers;
import org.ethereum.beacon.crypto.bls.bc.BCParameters;
import org.ethereum.beacon.crypto.bls.codec.Codec;
import org.ethereum.beacon.crypto.bls.codec.Validator;
import org.ethereum.beacon.crypto.bls.milagro.BIGs;
import org.ethereum.beacon.crypto.bls.milagro.MilagroMessageMapper;
import tech.pegasys.artemis.util.bytes.Bytes32;
import tech.pegasys.artemis.util.bytes.Bytes48;
import tech.pegasys.artemis.util.bytes.Bytes96;

/**
 * An implementation of {@code BLS12-381} signature scheme in application to eth2.0 beacon chain.
 *
 * <p>Current implementation uses Milagro library to handle elliptic curve mathematics. And Bouncy
 * Castle library for key pair generation.
 *
 * <p>Scheme is described in <a
 * href="https://github.com/zkcrypto/pairing/tree/master/src/bls12_381">https://github.com/zkcrypto/pairing/tree/master/src/bls12_381</a>.
 * With some additions in <a
 * href="https://github.com/ethereum/eth2.0-specs/blob/master/specs/bls_signature.md">https://github.com/ethereum/eth2.0-specs/blob/master/specs/bls_signature.md</a>
 *
 * <p>In this implementation public key is <code>G<sub>1</sub></code> subgroup member while
 * signature is <code>G<sub>2</sub></code> member. Verification is done via calculating two pairing
 * products and comparing the result of these calculations, to get verified successfully the result
 * should be equal: {@code e(PubKey, MessagePoint) == e(G1, Signature)}. Where {@code G1} is a
 * generator point of <code>G<sub>1</sub></code> subgroup.
 *
 * @see MessageParameters
 * @see Signature
 * @see PublicKey
 * @see <a href="https://github.com/milagro-crypto/milagro-crypto-java">Milagro Library</a>
 * @see <a href="https://github.com/bcgit/bc-java">Bouncy Castle</a>
 */
public class BLS381 {

  private static final String ALGORITHM = "BLS";

  private static final String KEY_GENERATOR_ALGORITHM = "ECDSA";
  private static final String KEY_GENERATOR_PROVIDER = "BC";

  private static final KeyPairGenerator KEY_PAIR_GENERATOR;
  private static final MessageParametersMapper<ECP2> MESSAGE_MAPPER;

  static {
    Security.addProvider(new BouncyCastleProvider());
    try {
      KEY_PAIR_GENERATOR =
          KeyPairGenerator.getInstance(KEY_GENERATOR_ALGORITHM, KEY_GENERATOR_PROVIDER);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    try {
      ECParameterSpec spec =
          new ECParameterSpec(
              BCParameters.G1.CURVE,
              BCParameters.G1.G,
              BCParameters.G1.CURVE.getOrder(),
              BCParameters.G1.CURVE.getCofactor());

      KEY_PAIR_GENERATOR.initialize(spec, new SecureRandom());
    } catch (InvalidAlgorithmParameterException e) {
      throw new RuntimeException(e);
    }

    MESSAGE_MAPPER = new MilagroMessageMapper();
  }

  /**
   * Signs message with given key pair.
   *
   * @param message a message.
   * @param keyPair a key pair.
   * @return calculated signature.
   */
  public static Signature sign(MessageParameters message, KeyPair keyPair) {
    ECP2 messagePoint = MESSAGE_MAPPER.map(message);
    ECP2 product = messagePoint.mul(keyPair.privateKey.asFieldElement());
    return Signature.create(product);
  }

  /**
   * Verifies message signature against given public key.
   *
   * @param message a message.
   * @param signature a signature.
   * @param publicKey a public key.
   * @return {@code true} if message has been signed with given public key, otherwise, {@code
   *     false}.
   */
  public static boolean verify(
      MessageParameters message, Signature signature, PublicKey publicKey) {
    ECP2 messagePoint = MESSAGE_MAPPER.map(message);
    FP12 lhs = pairingProduct(publicKey.asEcPoint(), messagePoint);
    FP12 rhs = pairingProduct(ECP.generator(), signature.asEcPoint());

    return lhs.equals(rhs);
  }

  /**
   * Verifies a signature created by aggregation of signatures for a number of distinct messages.
   *
   * <p>Private keys that messages has been signed with could, also, be different. This is handle
   * with a list of {@code publicKeys}. Index of message in the list must match the index of related
   * public key.
   *
   * @param messages a list of messages.
   * @param signature an aggregated signature.
   * @param publicKeys a list of public keys.
   * @return {@code true} if messages have been signed with given public keys, otherwise, {@code
   *     false}.
   * @throws AssertionError if {@code messages.size()} is not equal to {@code * publicKeys.size()}
   */
  public static boolean verifyMultiple(
      List<MessageParameters> messages, Signature signature, List<PublicKey> publicKeys) {
    assert messages.size() == publicKeys.size();

    FP12 lhs = new FP12(1);
    for (int i = 0; i < messages.size(); i++) {
      ECP2 messagePoint = MESSAGE_MAPPER.map(messages.get(i));
      FP12 product = PAIR.ate(messagePoint, publicKeys.get(i).asEcPoint());
      lhs.mul(product);
    }
    lhs = PAIR.fexp(lhs);
    FP12 rhs = pairingProduct(ECP.generator(), signature.asEcPoint());

    return lhs.equals(rhs);
  }

  /**
   * Calculates ate pairing product for given elliptic curve points.
   *
   * @param g1 elliptic curve point belonging to <code>G<sub>1</sub></code> subgroup.
   * @param g2 elliptic curve point belonging to <code>G<sub>2</sub></code> subgroup.
   * @return a member of cyclic subgroup of roots of unity in a finite field <code>
   *     F<sub>p<sup>12</sup></sub></code>.
   */
  private static FP12 pairingProduct(ECP g1, ECP2 g2) {
    FP12 ateProduct = PAIR.ate(g2, g1);
    return PAIR.fexp(ateProduct);
  }

  /** {@code BLS12-381} signature. */
  public static class Signature {

    /** Encoded <code>G<sub>2</sub></code> point that represents signature. */
    private final Bytes96 encoded;

    private Signature(Bytes96 encoded) {
      this.encoded = encoded;
    }

    /**
     * Creates signature from <code>G<sub>2</sub></code> point.
     *
     * @param ecPoint <code>G<sub>2</sub></code> point.
     * @return created signature.
     */
    public static Signature create(ECP2 ecPoint) {
      return new Signature(G2.encode(ecPoint));
    }

    /**
     * Creates signature from encoded <code>G<sub>2</sub></code> point.
     *
     * <p><strong>Note:</strong> runs encoding format validation.
     *
     * @param encoded encoded point.
     * @return an instance of signature.
     * @throws IllegalArgumentException if encoded point didn't pass the validation
     * @see Validator#G2
     */
    public static Signature create(Bytes96 encoded) {
      Validator.Result result = Validator.G2.validate(encoded);
      checkArgument(result.isValid(), "Failed to instantiate signature, %s", result.getMessage());

      if (!Codec.G2.decode(encoded).isInfinity()) {
        ECP2 point = G2.decode(encoded);
        checkArgument(
            !point.is_infinity(),
            "Failed to instantiate signature, given point is not a G2 member");

        // Multiply point by group order to check whether this point belongs to G2
        ECP2 orderCheck = point.mul(ORDER);
        checkArgument(
            orderCheck.is_infinity(),
            "Failed to instantiate signature, given point is not a G2 member");
      }

      return new Signature(encoded);
    }

    /**
     * Aggregates a list of signatures into a single one.
     *
     * <p>Signature aggregation in {@code BLS12-381} is a matter of calculating a sum of points
     * representing given signatures.
     *
     * @param signatures a list of signatures to be aggregated.
     * @return aggregated signature.
     */
    public static Signature aggregate(List<Signature> signatures) {
      ECP2 product = new ECP2();
      signatures.forEach(signature -> product.add(signature.asEcPoint()));
      return create(product);
    }

    public Bytes96 getEncoded() {
      return encoded;
    }

    /**
     * Decodes signature to {@link ECP2} point.
     *
     * @return signature point.
     */
    ECP2 asEcPoint() {
      return G2.decode(encoded);
    }
  }

  /**
   * {@code BLS12-381} private key.
   *
   * <p>Represented as a scalar with max value capped by {@link BCParameters#ORDER}. Hence, private
   * keys are {@code 32-bytes} length.
   */
  public static class PrivateKey implements java.security.PrivateKey {

    private final Bytes32 encoded;

    private PrivateKey(Bytes32 encoded) {
      this.encoded = encoded;
    }

    /**
     * Creates key from big integer value.
     *
     * @param value value.
     * @return created key.
     */
    public static PrivateKey create(BigInteger value) {
      byte[] rawBytes = BigIntegers.asUnsignedByteArray(Bytes32.SIZE, value);
      return new PrivateKey(Bytes32.wrap(rawBytes));
    }

    /**
     * Creates private key from a byte sequence.
     *
     * @param encoded byte sequence.
     * @return an instance of private key.
     */
    public static PrivateKey create(Bytes32 encoded) {
      return new PrivateKey(encoded);
    }

    @Override
    public String getAlgorithm() {
      return ALGORITHM;
    }

    @Override
    public String getFormat() {
      return null;
    }

    @Override
    public byte[] getEncoded() {
      return encoded.getArrayUnsafe();
    }

    public Bytes32 getEncodedBytes() {
      return encoded;
    }

    BIG asFieldElement() {
      return BIGs.fromBytes(encoded);
    }
  }

  /** {@code BLS12-381} public key. */
  public static class PublicKey implements java.security.PublicKey {

    private final Bytes48 encoded;

    private PublicKey(Bytes48 encoded) {
      this.encoded = encoded;
    }

    /**
     * Instantiates public key from a private key.
     *
     * @param privateKey private key.
     * @return an instance of public key.
     */
    public static PublicKey create(PrivateKey privateKey) {
      ECP product = ECP.generator().mul(privateKey.asFieldElement());
      return create(product);
    }

    /**
     * Instantiates key from <code>G<sub>1</sub></code> point of Milagro implementation.
     *
     * @param ecPoint <code>G<sub>1</sub></code> point.
     * @return an instance of public key.
     * @see ECP
     */
    public static PublicKey create(ECP ecPoint) {
      return new PublicKey(G1.encode(ecPoint));
    }

    /**
     * Instantiates key from <code>G<sub>1</sub></code> point of Bouncy Castle implementation.
     *
     * @param ecPoint <code>G<sub>1</sub></code> point.
     * @return an instance of public key.
     * @see ECPoint
     */
    public static PublicKey create(ECPoint ecPoint) {
      BIG x = BIGs.fromBigInteger(ecPoint.getAffineXCoord().toBigInteger());
      BIG y = BIGs.fromBigInteger(ecPoint.getAffineYCoord().toBigInteger());

      return new PublicKey(G1.encode(new ECP(x, y)));
    }

    /**
     * Instantiates key from encoded <code>G<sub>1</sub></code> point.
     *
     * <p><strong>Note:</strong> runs encoding format validation.
     *
     * @param encoded an encoded point.
     * @return an instance of public key.
     * @throws IllegalArgumentException if encoded point didn't pass the validation
     * @see Validator#G1
     */
    public static PublicKey create(Bytes48 encoded) {
      Validator.Result result = Validator.G1.validate(encoded);
      checkArgument(result.isValid(), "Failed to instantiate public key, %s", result.getMessage());

      if (!Codec.G1.decode(encoded).isInfinity()) {
        ECP point = G1.decode(encoded);

        checkArgument(
            !point.is_infinity(),
            "Failed to instantiate public key, given point is not a G1 member");

        // Multiply point by group order to check whether this point belongs to G1
        ECP orderCheck = point.mul(ORDER);
        checkArgument(
            orderCheck.is_infinity(),
            "Failed to instantiate public key, given point is not a G1 member");
      }

      return new PublicKey(encoded);
    }

    /**
     * Instantiates key from encoded <code>G<sub>1</sub></code> point.
     *
     * <p><strong>Note:</strong> opposite to {@link #create(Bytes48)} does not run format
     * validation.
     *
     * @param encoded an encoded point.
     * @return an instance of public key.
     */
    public static PublicKey createWithoutValidation(Bytes48 encoded) {
      return new PublicKey(encoded);
    }

    /**
     * Aggregates a list of public keys to a single one.
     *
     * <p>In {@code BLS12-381} public key aggregation is done through calculation of a sum of points
     * representing given public keys.
     *
     * @param publicKeys a list of public keys.
     * @return aggregated public key.
     */
    public static PublicKey aggregate(List<PublicKey> publicKeys) {
      ECP product = new ECP();
      publicKeys.forEach(publicKey -> product.add(publicKey.asEcPoint()));
      return create(product);
    }

    @Override
    public String getAlgorithm() {
      return ALGORITHM;
    }

    @Override
    public String getFormat() {
      return null;
    }

    @Override
    public byte[] getEncoded() {
      return encoded.getArrayUnsafe();
    }

    public Bytes48 getEncodedBytes() {
      return encoded;
    }

    /**
     * Decodes public key to {@link ECP} point.
     *
     * @return public key point.
     */
    ECP asEcPoint() {
      return G1.decode(encoded);
    }
  }

  /**
   * A key pair class copied from {@link java.security.KeyPair}.
   *
   * <p>The original class is compounded with {@link #generate()} method which randomly generates a
   * new key pair.
   */
  public static class KeyPair {

    private PrivateKey privateKey;
    private PublicKey publicKey;

    /**
     * Constructs a key pair from the given public key and private key.
     *
     * <p>Note that this constructor only stores references to the public and private key components
     * in the generated key pair. This is safe, because {@code Key} objects are immutable.
     *
     * @param publicKey the public key.
     * @param privateKey the private key.
     */
    public KeyPair(PublicKey publicKey, PrivateKey privateKey) {
      this.publicKey = publicKey;
      this.privateKey = privateKey;
    }

    public static KeyPair create(PrivateKey privateKey) {
      return new KeyPair(PublicKey.create(privateKey), privateKey);
    }

    public String asString() {
      return String.format("%s:%s", privateKey.encoded, publicKey.encoded);
    }

    /**
     * Generates a new key pair using Bouncy Castle key generator.
     *
     * @return newly generated key pair.
     */
    public static KeyPair generate() {
      java.security.KeyPair keyPairRaw = KEY_PAIR_GENERATOR.generateKeyPair();
      BCECPrivateKey privateKeyRaw = (BCECPrivateKey) keyPairRaw.getPrivate();
      BCECPublicKey publicKeyRaw = (BCECPublicKey) keyPairRaw.getPublic();

      PrivateKey privateKey = PrivateKey.create(privateKeyRaw.getD());
      PublicKey publicKey = PublicKey.create(publicKeyRaw.getQ());

      return new KeyPair(publicKey, privateKey);
    }

    /**
     * Returns a reference to the public key component of this key pair.
     *
     * @return a reference to the public key.
     */
    public PublicKey getPublic() {
      return publicKey;
    }

    /**
     * Returns a reference to the private key component of this key pair.
     *
     * @return a reference to the private key.
     */
    public PrivateKey getPrivate() {
      return privateKey;
    }
  }
}
