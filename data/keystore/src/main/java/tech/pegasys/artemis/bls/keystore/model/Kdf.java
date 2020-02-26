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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.google.common.base.MoreObjects;

public class Kdf {
  private final CryptoFunction cryptoFunction;
  private final KdfParam param;
  private final String message;

  @JsonCreator
  public Kdf(
      @JsonProperty(value = "function", required = true) final CryptoFunction cryptoFunction,
      @JsonProperty(value = "params", required = true)
          @JsonTypeInfo(
              use = JsonTypeInfo.Id.NAME,
              include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
              property = "function")
          @JsonSubTypes({
            @Type(value = SCryptParam.class, name = "scrypt"),
            @Type(value = Pbkdf2Param.class, name = "pbkdf2")
          })
          final KdfParam param,
      @JsonProperty(value = "message", required = true) final String message) {
    this.cryptoFunction = cryptoFunction;
    this.param = param;
    this.message = message;
  }

  public Kdf(final KdfParam kdfParam) {
    this(kdfParam.getCryptoFunction(), kdfParam, "");
  }

  @JsonProperty(value = "function")
  public CryptoFunction getCryptoFunction() {
    return cryptoFunction;
  }

  @JsonProperty(value = "params")
  public KdfParam getParam() {
    return param;
  }

  @JsonProperty(value = "message")
  public String getMessage() {
    return message;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("function", cryptoFunction)
        .add("params", param)
        .add("message", message)
        .toString();
  }
}
