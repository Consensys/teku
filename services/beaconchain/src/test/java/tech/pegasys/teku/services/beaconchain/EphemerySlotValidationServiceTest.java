/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.services.beaconchain;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

class EphemerySlotValidationServiceTest {
  @InjectMocks private EphemerySlotValidationService ephemerySlotValidationService;

  @BeforeEach
  void setUp() {
    ephemerySlotValidationService = new EphemerySlotValidationService();
  }

  //  @Test
  //  void should_identify_ephemery_network() {
  //    SpecConfig specConfig = mock(SpecConfig.class);
  //    //    when(spec.getGenesisSpecConfig().getDepositContractAddress())
  //    //            .thenReturn(EPHEMERY_DEPOSIT_CONTRACT_ADDRESS);
  //    when(specConfig.getDepositContractAddress()).thenReturn(EPHEMERY_DEPOSIT_CONTRACT_ADDRESS);
  //    boolean result = ephemerySlotValidationService.isEphemeryNetwork();
  //    assertThat(result).isTrue();
  //  }
  //
  //  @Test
  //  void should_calculate_max_slot_correctly() {
  //    SpecConfig mockSpecConfig = mock(SpecConfig.class);
  //    when(mockSpecConfig.getMinGenesisTime()).thenReturn(UInt64.valueOf(1727377200L));
  //    when(mockSpecConfig.getGenesisDelay()).thenReturn(UInt64.valueOf(600));
  //    when(mockSpecConfig.getSecondsPerSlot()).thenReturn(UInt64.valueOf(12));
  //    when(spec.getGenesisSpecConfig()).thenReturn(mockSpecConfig);
  //    UInt64 maxSlot = ephemerySlotValidationService.calculateEphemeryMaxSlot(spec);
  //    assertThat(maxSlot).isNotNegative();
  //  }
  //
  //  @Test
  //  void should_identify_ephemery_network() {
  //    SpecConfig mockGenesisConfig = mock(GenesisSpecConfig.class);
  //    when(mockGenesisConfig.getDepositContractAddress())
  //        .thenReturn(EPHEMERY_DEPOSIT_CONTRACT_ADDRESS);
  //
  //    when(spec.getGenesisSpecConfig()).thenReturn(mockGenesisConfig);
  //    boolean result = ephemerySlotValidationService.isEphemeryNetwork();
  //    assertThat(result).isTrue();
  //  }
  //
  //  @Test
  //  void should_handle_null_deposit_address() {
  //    GenesisSpecConfig mockGenesisConfig = mock(GenesisSpecConfig.class);
  //    when(mockGenesisConfig.getDepositContractAddress()).thenReturn(null);
  //    when(spec.getGenesisSpecConfig()).thenReturn(mockGenesisConfig);
  //    assertThrows(NullPointerException.class, () -> isEphemeryNetwork());
  //  }
  //
  //  @Test
  //  void should_identify_non_ephemery_network() {
  //    Eth1Address differentAddress =
  //        Eth1Address.fromHexString("0x1111111111111111111111111111111111111111");
  //
  //    SpecConfig mockGenesisConfig = mock(GenesisSpecConfig.class);
  //    when(mockGenesisConfig.getDepositContractAddress()).thenReturn(differentAddress);
  //
  //    when(spec.getGenesisSpecConfig()).thenReturn(mockGenesisConfig);
  //    boolean result = isEphemeryNetwork();
  //    assertThat(result).isFalse();
  //  }
  //
  //  @Test
  //  void should_handle_null_genesis_config() {
  //    when(spec.getGenesisSpecConfig()).thenReturn(null);
  //    assertThrows(
  //        NullPointerException.class, () -> ephemerySlotValidationService.isEphemeryNetwork());
  //  }

  @Test
  void shouldCompleteWhenServiceStartsAndStops() {
    SafeFuture<?> startFuture = ephemerySlotValidationService.doStart();
    SafeFuture<?> stopFuture = ephemerySlotValidationService.doStop();
    assertTrue(startFuture.isDone());
    assertTrue(stopFuture.isDone());
  }
}
