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

import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.impl.crypto.MacProvider;
import java.security.Key;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;

public class TestHelper {
  public static SecretKeySpec generateJwtSecret() {
    final Key key = MacProvider.generateKey(SignatureAlgorithm.HS256);
    final byte[] keyData = key.getEncoded();
    return new SecretKeySpec(keyData, SignatureAlgorithm.HS256.getJcaName());
  }

  public static boolean secretEquals(final Key expected, final Key actual) {
    return StringUtils.equals(
        Bytes.wrap(expected.getEncoded()).toHexString(),
        Bytes.wrap(actual.getEncoded()).toHexString());
  }
}
