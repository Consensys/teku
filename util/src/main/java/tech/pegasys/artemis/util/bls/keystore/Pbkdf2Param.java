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
public class Pbkdf2Param extends KdfParam {
  private Integer c;
  private String prf;

  @JsonCreator
  public Pbkdf2Param(
      @JsonProperty(value = "dklen", required = true) final Integer dklen,
      @JsonProperty(value = "c", required = true) final Integer c,
      @JsonProperty(value = "prf", required = true) final String prf,
      @JsonProperty(value = "salt", required = true) final Bytes salt) {
    super(dklen, salt);
    this.c = c;
    this.prf = prf;
  }

  @JsonProperty(value = "c")
  public Integer getC() {
    return c;
  }

  @JsonProperty(value = "prf")
  public String getPrf() {
    return prf;
  }
}
