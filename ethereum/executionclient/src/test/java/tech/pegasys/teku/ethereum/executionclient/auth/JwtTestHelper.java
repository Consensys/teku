/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.ethereum.executionclient.auth;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import javax.crypto.spec.SecretKeySpec;
import org.apache.tuweni.bytes.Bytes;

public class JwtTestHelper {
  public static SecretKeySpec generateJwtSecret() {
    final Key key = Keys.secretKeyFor(SignatureAlgorithm.HS256);
    final byte[] keyData = key.getEncoded();
    return new SecretKeySpec(keyData, SignatureAlgorithm.HS256.getJcaName());
  }

  public static void assertSecretEquals(final Key expected, final Key actual) {
    assertThat(Bytes.wrap(expected.getEncoded()).toHexString())
        .isEqualTo(Bytes.wrap(actual.getEncoded()).toHexString());
  }
}
