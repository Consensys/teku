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

package tech.pegasys.artemis.bls.keystore;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.bouncycastle.crypto.generators.SCrypt;

public class SCryptParam extends KdfParam {
  private final Integer n;
  private final Integer p;
  private final Integer r;

  /** Construct SCrypt Parameters. @See org.bouncycastle.crypto.generators.SCrypt */
  @JsonCreator
  public SCryptParam(
      @JsonProperty(value = "dklen", required = true) final Integer dklen,
      @JsonProperty(value = "n", required = true) final Integer n,
      @JsonProperty(value = "p", required = true) final Integer p,
      @JsonProperty(value = "r", required = true) final Integer r,
      @JsonProperty(value = "salt", required = true) final Bytes32 salt) {
    super(dklen, salt);
    this.n = n;
    this.p = p;
    this.r = r;
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
  public Bytes decryptionKey(final byte[] password) {
    return Bytes.wrap(
        SCrypt.generate(
            password, getSalt().toArrayUnsafe(), getN(), getR(), getP(), getDerivedKeyLength()));
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("dklen", getDerivedKeyLength())
        .add("n", n)
        .add("p", p)
        .add("r", r)
        .add("salt", getSalt())
        .toString();
  }
}
