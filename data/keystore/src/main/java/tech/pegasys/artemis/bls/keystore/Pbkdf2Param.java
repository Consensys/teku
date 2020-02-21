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

public class Pbkdf2Param extends KdfParam {
  private final Integer iterativeCount;
  private final Pbkdf2PseudoRandomFunction prf;

  @JsonCreator
  public Pbkdf2Param(
      @JsonProperty(value = "dklen", required = true) final Integer dklen,
      @JsonProperty(value = "c", required = true) final Integer iterativeCount,
      @JsonProperty(value = "prf", required = true) final Pbkdf2PseudoRandomFunction prf,
      @JsonProperty(value = "salt", required = true) final Bytes salt) {
    super(dklen, salt);
    this.iterativeCount = iterativeCount;
    this.prf = prf;
  }

  @JsonProperty(value = "c")
  public Integer getIterativeCount() {
    return iterativeCount;
  }

  @JsonProperty(value = "prf")
  public Pbkdf2PseudoRandomFunction getPrf() {
    return prf;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("dklen", getDerivedKeyLength())
        .add("c", iterativeCount)
        .add("prf", prf)
        .add("salt", getSalt())
        .toString();
  }
}
