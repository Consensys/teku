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

import com.fasterxml.jackson.annotation.JsonValue;

public enum CipherFunction {
  AES_128_CTR("aes-128-ctr", "AES/CTR/NoPadding");

  private final String jsonValue;
  private final String algorithmName;

  CipherFunction(final String jsonValue, final String algorithmName) {
    this.jsonValue = jsonValue;
    this.algorithmName = algorithmName;
  }

  @JsonValue
  public String getJsonValue() {
    return this.jsonValue;
  }

  public String getAlgorithmName() {
    return algorithmName;
  }
}
