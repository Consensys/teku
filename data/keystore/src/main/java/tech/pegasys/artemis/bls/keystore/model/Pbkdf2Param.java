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

package tech.pegasys.artemis.bls.keystore.model;

import static com.google.common.base.Preconditions.checkNotNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import java.nio.charset.StandardCharsets;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.util.DigestFactory;

public class Pbkdf2Param extends KdfParam {
  private static final int DEFAULT_DKLEN = 32;
  private static final int DEFAULT_COUNT = 262144;

  private final Integer c;
  private final Pbkdf2PseudoRandomFunction prf;

  /**
   * PBKDF2 Key Derivation Function
   *
   * @param dklen The length of key to generate
   * @param c The iteration count
   * @param prf The pseudo random function i.e. hash digest to use.
   * @param salt The salt to use
   */
  @JsonCreator
  public Pbkdf2Param(
      @JsonProperty(value = "dklen", required = true) final Integer dklen,
      @JsonProperty(value = "c", required = true) final Integer c,
      @JsonProperty(value = "prf", required = true) final Pbkdf2PseudoRandomFunction prf,
      @JsonProperty(value = "salt", required = true) final Bytes salt) {
    super(dklen, salt);
    this.c = c;
    this.prf = prf;
  }

  public Pbkdf2Param() {
    this(DEFAULT_DKLEN, DEFAULT_COUNT, Pbkdf2PseudoRandomFunction.HMAC_SHA256, Bytes32.random());
  }

  public Pbkdf2Param(final Bytes salt) {
    this(DEFAULT_DKLEN, DEFAULT_COUNT, Pbkdf2PseudoRandomFunction.HMAC_SHA256, salt);
  }

  @JsonProperty(value = "c")
  public Integer getC() {
    return c;
  }

  @JsonProperty(value = "prf")
  public Pbkdf2PseudoRandomFunction getPrf() {
    return prf;
  }

  @Override
  public CryptoFunction getCryptoFunction() {
    return CryptoFunction.PBKDF2;
  }

  @Override
  public Bytes generateDecryptionKey(final String password) {
    checkNotNull(password, "Password is required");
    final PKCS5S2ParametersGenerator gen =
        new PKCS5S2ParametersGenerator(DigestFactory.createSHA256());
    gen.init(password.getBytes(StandardCharsets.UTF_8), getSalt().toArrayUnsafe(), getC());
    final int keySizeInBits = getDkLen() * 8;
    final byte[] key = ((KeyParameter) gen.generateDerivedParameters(keySizeInBits)).getKey();
    return Bytes.wrap(key);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("dklen", getDkLen())
        .add("c", c)
        .add("prf", prf)
        .add("salt", getSalt())
        .toString();
  }
}
