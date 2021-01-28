/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.networking.nat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

public class NatServiceTest {
  private NatManager natManager = mock(NatManager.class);
  private Optional<NatManager> maybeNatManager = Optional.of(natManager);

  @Test
  public void shouldRequestPortsBeMappedOnServiceStart() {
    final NatService natService = new NatService(9000, true, maybeNatManager);
    when(natManager.start()).thenReturn(SafeFuture.completedFuture(null));
    assertThat(natService.start()).isCompleted();
    verify(natManager).start();
    verify(natManager).requestPortForward(eq(9000), eq(NetworkProtocol.UDP), any());
    verify(natManager).requestPortForward(eq(9000), eq(NetworkProtocol.TCP), any());
    verifyNoMoreInteractions(natManager);
  }

  @Test
  public void shouldShutdownNatManager() {
    final NatService natService = new NatService(9000, true, maybeNatManager);
    when(natManager.start()).thenReturn(SafeFuture.completedFuture(null));
    assertThat(natService.start()).isCompleted();

    assertThat(natService.stop()).isCompleted();
    verify(natManager).stop();
  }
}
