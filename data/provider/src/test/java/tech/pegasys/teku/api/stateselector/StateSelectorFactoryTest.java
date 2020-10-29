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

package tech.pegasys.teku.api.stateselector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class StateSelectorFactoryTest {

  private final CombinedChainDataClient client = mock(CombinedChainDataClient.class);
  private final DataStructureUtil data = new DataStructureUtil();
  private final BeaconState state = data.randomBeaconState();
  private final StateSelectorFactory factory = new StateSelectorFactory(client);

  @Test
  public void headSelector_shouldGetBestState() throws ExecutionException, InterruptedException {
    when(client.getBestState()).thenReturn(Optional.of(state));
    Optional<BeaconState> result = factory.headSelector().getState().get();
    assertThat(result).isEqualTo(Optional.of(state));
    verify(client).getBestState();
  }

  @Test
  public void finalizedSelector_shouldGetFinalizedState()
      throws ExecutionException, InterruptedException {
    when(client.getFinalizedState()).thenReturn(Optional.of(state));
    Optional<BeaconState> result = factory.finalizedSelector().getState().get();
    assertThat(result).isEqualTo(Optional.of(state));
    verify(client).getFinalizedState();
  }

  @Test
  public void justifiedSelector_shouldGetJustifiedState()
      throws ExecutionException, InterruptedException {
    when(client.getJustifiedState()).thenReturn(SafeFuture.completedFuture(Optional.of(state)));
    Optional<BeaconState> result = factory.justifiedSelector().getState().get();
    assertThat(result).isEqualTo(Optional.of(state));
    verify(client).getJustifiedState();
  }

  @Test
  public void genesisSelector_shouldGetStateAtSlotExact()
      throws ExecutionException, InterruptedException {
    when(client.getStateAtSlotExact(UInt64.ZERO))
        .thenReturn(SafeFuture.completedFuture(Optional.of(state)));
    Optional<BeaconState> result = factory.genesisSelector().getState().get();
    assertThat(result).isEqualTo(Optional.of(state));
    verify(client).getStateAtSlotExact(UInt64.ZERO);
  }

  @Test
  public void forSlot_shouldGetStateAtSlotExact() throws ExecutionException, InterruptedException {
    when(client.getStateAtSlotExact(state.getSlot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(state)));
    Optional<BeaconState> result = factory.forSlot(state.getSlot()).getState().get();
    assertThat(result).isEqualTo(Optional.of(state));
    verify(client).getStateAtSlotExact(state.getSlot());
  }

  @Test
  public void forStateRoot_shouldGetStateAtSlotExact()
      throws ExecutionException, InterruptedException {
    when(client.getStateByStateRoot(state.hash_tree_root()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(state)));
    Optional<BeaconState> result = factory.forStateRoot(state.hash_tree_root()).getState().get();
    assertThat(result).isEqualTo(Optional.of(state));
    verify(client).getStateByStateRoot(state.hash_tree_root());
  }

  @Test
  public void defaultBlockSelector_shouldThrowBadRequestException() {
    assertThrows(BadRequestException.class, () -> factory.defaultStateSelector("a"));
  }
}
