/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.ethereum.executionlayer.client.auth;

public class JwtConfig {
  private String hexEncodedSecretKey;

  private final long expiresInSeconds = 5;

  public JwtConfig(final String hexEncodedSecretKey) {
    this.hexEncodedSecretKey = hexEncodedSecretKey;
  }

  public String getHexEncodedSecretKey() {
    return hexEncodedSecretKey;
  }

  public void setHexEncodedSecretKey(String hexEncodedSecretKey) {
    this.hexEncodedSecretKey = hexEncodedSecretKey;
  }

  public long getExpiresInSeconds() {
    return expiresInSeconds;
  }
}
