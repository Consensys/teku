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

import static tech.pegasys.artemis.bls.keystore.KeyStorePreConditions.checkArgument;
import static tech.pegasys.artemis.bls.keystore.KeyStorePreConditions.checkNotNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import java.nio.charset.StandardCharsets;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.util.DigestFactory;
import tech.pegasys.artemis.bls.keystore.KeyStoreValidationException;

public class Pbkdf2Param extends KdfParam {

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
      @JsonProperty(value = "dklen", required = true) final int dklen,
      @JsonProperty(value = "c", required = true) final int c,
      @JsonProperty(value = "prf", required = true) final Pbkdf2PseudoRandomFunction prf,
      @JsonProperty(value = "salt", required = true) final Bytes salt) {
    super(dklen, salt);
    this.c = c;
    this.prf = prf;
    validateParams();
  }

  private void validateParams() throws KeyStoreValidationException {
    checkArgument(c >= 1, "Iterative Count parameter c must be >= 1");
    // because the EIP-2335 spec requires dklen >= 32
    checkArgument(getDkLen() >= 32, "Generated key length dkLen must be >= 32.");
    checkNotNull(getSalt(), "salt cannot be null");
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
