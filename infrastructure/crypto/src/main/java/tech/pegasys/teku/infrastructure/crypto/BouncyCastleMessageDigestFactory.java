/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.teku.infrastructure.crypto;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

public class BouncyCastleMessageDigestFactory {

  private static final Provider sha256Provider = selectSha256Provider();

  private static Provider selectSha256Provider() {
    // Use the SUN provider if it's available. It's still a known good implementation but uses
    // intrinsics to provide optimised native implementations
    final Provider sunProvider = Security.getProvider("SUN");
    return sunProvider != null ? sunProvider : new BouncyCastleProvider();
  }

  @SuppressWarnings("DoNotInvokeMessageDigestDirectly")
  public static MessageDigest createSha256() throws NoSuchAlgorithmException {
    return MessageDigest.getInstance("SHA-256", sha256Provider);
  }
}
