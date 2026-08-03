/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.networking.p2p.libp2p.rpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import org.junit.jupiter.api.Test;

class InboundRpcStreamLimiterTest {

  @Test
  void shouldLimitActiveStreamsAndAllowNewStreamsAfterRelease() {
    final InboundRpcStreamLimiter limiter = new InboundRpcStreamLimiter(2);

    assertThat(limiter.tryAcquire()).isTrue();
    assertThat(limiter.tryAcquire()).isTrue();
    assertThat(limiter.tryAcquire()).isFalse();
    assertThat(limiter.getActiveStreams()).isEqualTo(2);

    limiter.release();

    assertThat(limiter.tryAcquire()).isTrue();
    assertThat(limiter.getActiveStreams()).isEqualTo(2);
  }

  @Test
  void shouldRejectNonPositiveLimits() {
    assertThatIllegalArgumentException().isThrownBy(() -> new InboundRpcStreamLimiter(0));
  }

  @Test
  void shouldRejectReleaseWithoutAcquisition() {
    final InboundRpcStreamLimiter limiter = new InboundRpcStreamLimiter(1);

    assertThatIllegalStateException().isThrownBy(limiter::release);
  }
}
