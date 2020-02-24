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
import tech.pegasys.artemis.bls.keystore.model.SCryptParam;

public final class SCryptParamBuilder {
  private Integer dklen = 32;
  private Bytes32 salt;
  private Integer n = 262144;
  private Integer p = 1;
  private Integer r = 8;

  private SCryptParamBuilder() {}

  public static SCryptParamBuilder aSCryptParam() {
    return new SCryptParamBuilder();
  }

  public SCryptParamBuilder withDklen(final Integer dklen) {
    this.dklen = dklen;
    return this;
  }

  public SCryptParamBuilder withSalt(final Bytes32 salt) {
    this.salt = salt;
    return this;
  }

  public SCryptParamBuilder withN(final Integer n) {
    this.n = n;
    return this;
  }

  public SCryptParamBuilder withP(final Integer p) {
    this.p = p;
    return this;
  }

  public SCryptParamBuilder withR(final Integer r) {
    this.r = r;
    return this;
  }

  public SCryptParam build() {
    if (salt == null) {
      // generate random salt of 32 bytes
      salt = Bytes32.random();
    }
    return new SCryptParam(dklen, n, p, r, salt);
  }
}
