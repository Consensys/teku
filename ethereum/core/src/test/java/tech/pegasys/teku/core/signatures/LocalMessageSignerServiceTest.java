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

package tech.pegasys.teku.core.signatures;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;

class LocalMessageSignerServiceTest {
  private static final Bytes MESSAGE = Bytes.fromHexString("0x123456");
  private static final BLSKeyPair KEYPAIR = BLSKeyPair.random(1234);
  private static final BLSSignature EXPECTED_SIGNATURE = BLS.sign(KEYPAIR.getSecretKey(), MESSAGE);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();

  private final LocalMessageSignerService signerService =
      new LocalMessageSignerService(KEYPAIR, asyncRunner);

  @ParameterizedTest(name = "{0}")
  @MethodSource("signMethods")
  @SuppressWarnings("unchecked")
  void shouldSignAsynchronously(@SuppressWarnings("unused") final String name, final Method method)
      throws Exception {
    final SafeFuture<BLSSignature> result =
        (SafeFuture<BLSSignature>) method.invoke(signerService, MESSAGE);
    assertThat(result).isNotDone();

    asyncRunner.executeQueuedActions();

    assertThat(result).isCompletedWithValue(EXPECTED_SIGNATURE);
  }

  static List<Arguments> signMethods() {
    return Stream.of(MessageSignerService.class.getMethods())
        .filter(method -> method.getName().startsWith("sign"))
        .map(method -> Arguments.of(method.getName(), method))
        .collect(Collectors.toList());
  }
}
