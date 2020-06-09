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

package tech.pegasys.teku.logging;

import static tech.pegasys.teku.logging.ColorConsolePrinter.print;
import static tech.pegasys.teku.logging.LoggingConfigurator.VALIDATOR_LOGGER_NAME;

import com.google.common.base.Strings;
import com.google.common.primitives.UnsignedLong;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.logging.ColorConsolePrinter.Color;

public class ValidatorLogger {
  public static final ValidatorLogger VALIDATOR_LOGGER = new ValidatorLogger(VALIDATOR_LOGGER_NAME);
  public static final int LONGEST_TYPE_LENGTH = "attestation".length();
  private static final String PREFIX = "Validator   *** ";

  private final Logger log;

  private ValidatorLogger(final String name) {
    this.log = LogManager.getLogger(name);
  }

  public void dutyCompleted(
      final String producedType,
      final UnsignedLong slot,
      final int successCount,
      final Set<Bytes32> blockRoots) {
    final String paddedType = Strings.padEnd(producedType, LONGEST_TYPE_LENGTH, ' ');
    logDuty(paddedType, slot, successCount, blockRoots);
  }

  public void dutySkippedWhileSyncing(
      final String producedType, final UnsignedLong slot, final int skippedCount) {
    log.warn(
        print(
            String.format(
                "%sSkipped producing %s while node is syncing  Count: %s, Slot: %s",
                PREFIX, producedType, skippedCount, slot),
            Color.YELLOW));
  }

  public void dutyFailed(
      final String producedType, final UnsignedLong slot, final Throwable error) {
    log.error(
        print(
            String.format("%sFailed to produce %s  Slot: %s", PREFIX, producedType, slot),
            Color.RED),
        error);
  }

  private void logDuty(
      final String type, final UnsignedLong slot, final int count, final Set<Bytes32> roots) {
    log.info(
        print(
            String.format(
                "%sPublished %s  Count: %s, Slot: %s, Root: %s",
                PREFIX, type, count, slot, formatBlockRoots(roots)),
            Color.BLUE));
  }

  private String formatBlockRoots(final Set<Bytes32> blockRoots) {
    return blockRoots.stream().map(LogFormatter::formatHashRoot).collect(Collectors.joining(", "));
  }

  public void aggregationSkipped(final UnsignedLong slot) {
    log.warn(
        print(
            PREFIX
                + "Skipped aggregation for slot "
                + slot
                + " because there was nothing to aggregate",
            Color.YELLOW));
  }

  public void producedInvalidAttestation(final UnsignedLong slot, final String reason) {
    log.error(
        print(PREFIX + "Produced invalid attestation for slot " + slot + ": " + reason, Color.RED));
  }

  public void producedInvalidAggregate(final UnsignedLong slot, final String reason) {
    log.error(
        print(PREFIX + "Produced invalid aggregate for slot " + slot + ": " + reason, Color.RED));
  }
}
