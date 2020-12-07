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

package tech.pegasys.teku.test.acceptance.dsl.tools.deposits;

import java.security.SecureRandom;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.infrastructure.crypto.SecureRandomProvider;

public class ValidatorKeyGenerator {
  private final int validatorCount;
  private final SecureRandom srng;

  public ValidatorKeyGenerator(final int validatorCount) {
    this.validatorCount = validatorCount;
    this.srng = SecureRandomProvider.createSecureRandom();
  }

  public Stream<ValidatorKeys> generateKeysStream() {
    return IntStream.range(0, validatorCount).mapToObj(ignore -> generateKey());
  }

  private ValidatorKeys generateKey() {
    final BLSKeyPair validatorKey = BLSKeyPair.random(srng);
    final BLSKeyPair withdrawalKey = BLSKeyPair.random(srng);
    return new ValidatorKeys(validatorKey, withdrawalKey);
  }
}
