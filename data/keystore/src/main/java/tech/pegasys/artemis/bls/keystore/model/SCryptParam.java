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

import static java.nio.charset.StandardCharsets.UTF_8;
import static tech.pegasys.artemis.bls.keystore.KeyStorePreConditions.checkArgument;
import static tech.pegasys.artemis.bls.keystore.KeyStorePreConditions.checkNotNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.crypto.generators.SCrypt;
import tech.pegasys.artemis.bls.keystore.KeyStoreValidationException;

public class SCryptParam extends KdfParam {
  private final int n;
  private final int p;
  private final int r;

  /**
   * SCrypt Key Derivation Function
   *
   * @param dklen The length of key to generate.
   * @param n CPU/Memory cost parameter. Must be larger than 1, a power of 2 and less than <code>
   *     2^(128 * r / 8)</code>.
   * @param p Parallelization parameter. Must be a positive integer less than or equal to <code>
   *     Integer.MAX_VALUE / (128 * r * 8)</code>
   * @param r the block size, must be &gt;= 1.
   * @param salt The salt to use
   */
  @JsonCreator
  public SCryptParam(
      @JsonProperty(value = "dklen", required = true) final int dklen,
      @JsonProperty(value = "n", required = true) final int n,
      @JsonProperty(value = "p", required = true) final int p,
      @JsonProperty(value = "r", required = true) final int r,
      @JsonProperty(value = "salt", required = true) final Bytes salt) {
    super(dklen, salt);
    this.n = n;
    this.p = p;
    this.r = r;
    validateParams();
  }

  private void validateParams() throws KeyStoreValidationException {
    checkArgument(n > 1 && isPowerOf2(n), "Cost parameter n must be > 1 and a power of 2");
    // Only value of r that cost (as an int) could be exceeded for is 1
    if (r == 1) {
      checkArgument(n > 1 && n < 65536, "Cost parameter n must be > 1 and < 65536.");
    }

    checkArgument(r >= 1, "Block size r must be >= 1.");

    int maxParallel = Integer.MAX_VALUE / (128 * r * 8);
    checkArgument(
        p >= 1 && p <= maxParallel,
        String.format(
            "Parallelization parameter p must be >= 1 and <= %d (based on block size r of %d",
            maxParallel, r));
    // because the EIP-2335 spec requires dklen >= 32
    checkArgument(getDkLen() >= 32, "Generated key length dkLen must be >= 32.");
  }

  @JsonProperty(value = "n")
  public Integer getN() {
    return n;
  }

  @JsonProperty(value = "p")
  public Integer getP() {
    return p;
  }

  @JsonProperty(value = "r")
  public Integer getR() {
    return r;
  }

  @Override
  public CryptoFunction getCryptoFunction() {
    return CryptoFunction.SCRYPT;
  }

  @Override
  public Bytes generateDecryptionKey(final String password) {
    checkNotNull(password, "Password is required");
    return Bytes.wrap(
        SCrypt.generate(
            password.getBytes(UTF_8),
            getSalt().toArrayUnsafe(),
            getN(),
            getR(),
            getP(),
            getDkLen()));
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("dklen", getDkLen())
        .add("n", n)
        .add("p", p)
        .add("r", r)
        .add("salt", getSalt())
        .toString();
  }

  private static boolean isPowerOf2(int x) {
    return ((x & (x - 1)) == 0);
  }
}
