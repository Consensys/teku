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

import com.amazon.corretto.crypto.provider.AmazonCorrettoCryptoProvider;
import com.amazon.corretto.crypto.provider.SelfTestStatus;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

public class MessageDigestFactory {
  private static final Provider securityProvider;
  private static final String selectionInfo;

  static {
    final Throwable loadingError = AmazonCorrettoCryptoProvider.INSTANCE.getLoadingError();
    if (loadingError != null) {
      selectionInfo = "Using BouncyCastle. ACCP not supported on this platform.";
      securityProvider = new BouncyCastleProvider();
    } else {
      final SelfTestStatus selfTestStatus = AmazonCorrettoCryptoProvider.INSTANCE.runSelfTests();
      if (selfTestStatus.equals(SelfTestStatus.PASSED)) {
        selectionInfo = "Using ACCP";
        AmazonCorrettoCryptoProvider.install();
        securityProvider = AmazonCorrettoCryptoProvider.INSTANCE;
      } else {
        selectionInfo = "Using BouncyCastle. ACCP not supported: " + selfTestStatus;
        securityProvider = new BouncyCastleProvider();
      }
    }
  }

  @SuppressWarnings("DoNotInvokeMessageDigestDirectly")
  public static MessageDigest create(String algorithm) throws NoSuchAlgorithmException {
    return MessageDigest.getInstance(algorithm, securityProvider);
  }

  public static String getProviderSelectionInfo() {
    return selectionInfo;
  }
}
