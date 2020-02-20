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

package tech.pegasys.artemis.util.bls.keystore;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import org.apache.tuweni.bytes.Bytes;

public class Checksum {
  private CryptoFunction cryptoFunction;
  private Param param;
  private Bytes message;

  @JsonCreator
  public Checksum(
      @JsonProperty(value = "function", required = true) final CryptoFunction cryptoFunction,
      @JsonProperty(value = "params", required = true) final Param param,
      @JsonProperty(value = "message", required = true) final Bytes message) {
    this.cryptoFunction = cryptoFunction;
    this.param = param;
    this.message = message;
  }

  @JsonProperty(value = "function")
  public CryptoFunction getCryptoFunction() {
    return cryptoFunction;
  }

  @JsonProperty(value = "params")
  public Param getParam() {
    return param;
  }

  @JsonProperty(value = "message")
  public Bytes getMessage() {
    return message;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("cryptoFunction", cryptoFunction)
        .add("param", param)
        .add("message", message)
        .toString();
  }
}
