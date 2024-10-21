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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.client.RecentChainData;

class EphemerySlotValidationServiceTest {
  private RecentChainData recentChainData;
  private EphemerySlotValidationService ephemerySlotValidationService;

  @BeforeEach
  void setUp() {
    recentChainData = mock(RecentChainData.class);
    ephemerySlotValidationService = new EphemerySlotValidationService(recentChainData);
  }

  @Test
  void shouldNotThrowWhenSlotIsWithinAllowedRange() {
    UInt64 currentSlot = UInt64.valueOf(10);
    UInt64 validSlot = currentSlot.plus(4);
    when(recentChainData.getCurrentSlot()).thenReturn(Optional.of(currentSlot));
    assertDoesNotThrow(() -> ephemerySlotValidationService.onSlot(validSlot));
  }

  @Test
  void shouldThrowWhenSlotIsTooFarAhead() {
    UInt64 currentSlot = UInt64.valueOf(10);
    final UInt64 maxSlot = currentSlot.max(recentChainData.getCurrentSlot().orElse(ZERO));
    UInt64 invalidSlot = currentSlot.plus(maxSlot.plus(1));
    when(recentChainData.getCurrentSlot()).thenReturn(Optional.of(currentSlot));
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class, () -> ephemerySlotValidationService.onSlot(invalidSlot));
    assertTrue(exception.getMessage().contains("is too far ahead"));
  }

  @Test
  void shouldThrowWhenCurrentSlotIsNotAvailable() {
    when(recentChainData.getCurrentSlot()).thenReturn(Optional.empty());
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> ephemerySlotValidationService.onSlot(UInt64.valueOf(10)));
    assertEquals("Current slot not available", exception.getMessage());
  }

  @Test
  void shouldCompleteWhenServiceStartsAndStops() {
    SafeFuture<?> startFuture = ephemerySlotValidationService.doStart();
    SafeFuture<?> stopFuture = ephemerySlotValidationService.doStop();
    assertTrue(startFuture.isDone());
    assertTrue(stopFuture.isDone());
  }
}
