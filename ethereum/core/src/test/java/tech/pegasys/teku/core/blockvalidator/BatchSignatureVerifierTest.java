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

package tech.pegasys.teku.core.blockvalidator;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;

public class BatchSignatureVerifierTest {

  @Test
  public void testParallel() throws Exception {
    BatchSignatureVerifier verifier = new BatchSignatureVerifier();

    BLSPublicKey publicKey = BLSPublicKey.random(42);
    Bytes message = Bytes.wrap("Hello, world!".getBytes(UTF_8));
    BLSSignature signature = BLSSignature.random(42);

    int nThreads = 64;
    int iterations = 64;
    ExecutorService executor = Executors.newFixedThreadPool(nThreads);
    CountDownLatch latch = new CountDownLatch(nThreads);
    List<Future<Boolean>> futures =
        IntStream.range(0, nThreads)
            .mapToObj(
                __ ->
                    executor.submit(
                        () -> {
                          latch.countDown();
                          latch.await();
                          boolean ret = true;
                          for (int i = 0; i < iterations; i++) {
                            ret &=
                                verifier.verify(
                                    Collections.singletonList(publicKey), message, signature);
                          }
                          return ret;
                        }))
            .collect(Collectors.toList());

    for (Future<Boolean> future : futures) {
      assertThat(future.get()).isTrue();
    }
    assertThat(verifier.toVerify.size()).isEqualTo(nThreads * iterations);
    assertThat(verifier.toVerify).doesNotContainNull();
  }
}
