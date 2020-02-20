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
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.apache.tuweni.bytes.Bytes;

// Required because base class' usage of custom deserializer which produces an infinite loop
@JsonDeserialize(using = JsonDeserializer.None.class)
public class SCryptParam extends KdfParam {
  private Integer n;
  private Integer p;
  private Integer r;

  @JsonCreator
  public SCryptParam(
      @JsonProperty(value = "dklen", required = true) final Integer dklen,
      @JsonProperty(value = "n", required = true) final Integer n,
      @JsonProperty(value = "p", required = true) final Integer p,
      @JsonProperty(value = "r", required = true) final Integer r,
      @JsonProperty(value = "salt", required = true) final Bytes salt) {
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
}
