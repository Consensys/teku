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

package tech.pegasys.artemis.bls.keystore.builder;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.bls.keystore.Pbkdf2Param;
import tech.pegasys.artemis.bls.keystore.Pbkdf2PseudoRandomFunction;

public final class Pbkdf2ParamBuilder {
  private Integer dklen = 32;
  private Bytes32 salt;
  private Integer iterativeCount = 262144;

  private Pbkdf2ParamBuilder() {}

  public static Pbkdf2ParamBuilder aPbkdf2Param() {
    return new Pbkdf2ParamBuilder();
  }

  public Pbkdf2ParamBuilder withDklen(final Integer dklen) {
    this.dklen = dklen;
    return this;
  }

  public Pbkdf2ParamBuilder withSalt(final Bytes32 salt) {
    this.salt = salt;
    return this;
  }

  public Pbkdf2ParamBuilder withIterativeCount(final Integer iterativeCount) {
    this.iterativeCount = iterativeCount;
    return this;
  }

  public Pbkdf2Param build() {
    if (salt == null) {
      salt = Bytes32.random();
    }
    return new Pbkdf2Param(dklen, iterativeCount, Pbkdf2PseudoRandomFunction.HMAC_SHA256, salt);
  }
}
