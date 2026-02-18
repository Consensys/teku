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

package tech.pegasys.teku.validator.beaconnode;

import static tech.pegasys.teku.infrastructure.time.TimeUtilities.secondsToMillis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.timed.RepeatingTaskScheduler;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;

/**
 * Coordinates per-milestone {@link TimeBasedEventAdapter} instances, chaining them together so that
 * each adapter's events expire at the next fork boundary that changes timing. Only three timing
 * profiles exist today: PHASE0 (attestation, aggregation), ALTAIR–FULU (adds sync committee,
 * contribution), and GLOAS (changes offsets, adds payload attestation).
 *
 * <p>Each adapter schedules all of its events (including the slot event). When an adapter's events
 * expire at a fork boundary, the next adapter in the chain is started for the first slot of the new
 * era.
 */
public class ForkAwareTimeBasedEventAdapter implements BeaconChainEventAdapter {
  private static final Logger LOG = LogManager.getLogger();

  private final GenesisDataProvider genesisDataProvider;
  private final TimeProvider timeProvider;
  private final Spec spec;

  private UInt64 genesisTimeMillis;

  // Keyed by the milestone that introduces a timing change: PHASE0, ALTAIR, GLOAS
  private final Map<SpecMilestone, TimeBasedEventAdapter> adapters = new LinkedHashMap<>();

  record ScheduledAdapter(TimeBasedEventAdapter adapter, Optional<UInt64> expiryMillis) {}

  public ForkAwareTimeBasedEventAdapter(
      final GenesisDataProvider genesisDataProvider,
      final RepeatingTaskScheduler taskScheduler,
      final TimeProvider timeProvider,
      final ValidatorTimingChannel validatorTimingChannel,
      final Spec spec) {
    this.genesisDataProvider = genesisDataProvider;
    this.timeProvider = timeProvider;
    this.spec = spec;

    adapters.put(
        SpecMilestone.PHASE0,
        new Phase0TimeBasedEventAdapter(taskScheduler, timeProvider, validatorTimingChannel, spec));
    if (spec.isMilestoneSupported(SpecMilestone.ALTAIR)) {
      adapters.put(
          SpecMilestone.ALTAIR,
          new AltairTimeBasedEventAdapter(
              taskScheduler, timeProvider, validatorTimingChannel, spec));
    }
    if (spec.isMilestoneSupported(SpecMilestone.GLOAS)) {
      adapters.put(
          SpecMilestone.GLOAS,
          new GloasTimeBasedEventAdapter(
              taskScheduler, timeProvider, validatorTimingChannel, spec));
    }
  }

  @Override
  public SafeFuture<Void> start() {
    genesisDataProvider.getGenesisTime().thenAccept(this::startWithGenesisTime).finishError(LOG);
    return SafeFuture.COMPLETE;
  }

  @Override
  public SafeFuture<Void> stop() {
    return SafeFuture.COMPLETE;
  }

  void startWithGenesisTime(final UInt64 genesisTime) {
    this.genesisTimeMillis = secondsToMillis(genesisTime);

    // Set genesis time on all adapters
    adapters.values().forEach(adapter -> adapter.setGenesisTime(genesisTime));

    final UInt64 currentSlot = getCurrentSlot();
    final UInt64 nextSlotStartMillis = getSlotStartTimeMillis(currentSlot.increment());

    // Build and start the adapter chain
    final List<ScheduledAdapter> scheduledAdapters = buildChain(currentSlot);
    activateAdapter(scheduledAdapters, 0, nextSlotStartMillis);
  }

  /**
   * Builds an ordered list of adapters to run, starting from the adapter whose era includes {@code
   * currentSlot}. Each entry carries an optional expiry time (the slot-start millis of the next
   * adapter's first slot). Adapters for eras already passed are skipped; the final adapter in the
   * chain has no expiry.
   */
  private List<ScheduledAdapter> buildChain(final UInt64 currentSlot) {
    final SpecMilestone currentMilestone = spec.atSlot(currentSlot).getMilestone();
    final List<ScheduledAdapter> chain = new ArrayList<>();

    // Phase0 adapter: only if we're currently in Phase0
    if (!currentMilestone.isGreaterThanOrEqualTo(SpecMilestone.ALTAIR)) {
      final Optional<UInt64> expiryMillis =
          Optional.ofNullable(adapters.get(SpecMilestone.ALTAIR))
              .map(adapter -> getSlotStartTimeMillis(adapter.getFirstSlot()));
      chain.add(new ScheduledAdapter(adapters.get(SpecMilestone.PHASE0), expiryMillis));
    }

    // Altair adapter: if ALTAIR is supported and we're not yet in GLOAS
    Optional.ofNullable(adapters.get(SpecMilestone.ALTAIR))
        .filter(__ -> !currentMilestone.isGreaterThanOrEqualTo(SpecMilestone.GLOAS))
        .ifPresent(
            altairAdapter -> {
              final Optional<UInt64> expiryMillis =
                  Optional.ofNullable(adapters.get(SpecMilestone.GLOAS))
                      .map(adapter -> getSlotStartTimeMillis(adapter.getFirstSlot()));
              chain.add(new ScheduledAdapter(altairAdapter, expiryMillis));
            });

    // Gloas adapter: if GLOAS is supported (always last, no expiry)
    Optional.ofNullable(adapters.get(SpecMilestone.GLOAS))
        .ifPresent(gloasAdapter -> chain.add(new ScheduledAdapter(gloasAdapter, Optional.empty())));

    return chain;
  }

  void activateAdapter(
      final List<ScheduledAdapter> scheduledAdapters, final int index, final UInt64 startFrom) {
    if (index >= scheduledAdapters.size()) {
      return;
    }
    final ScheduledAdapter adapter = scheduledAdapters.get(index);
    final AtomicBoolean transitionedToNext = new AtomicBoolean(false);
    final Runnable onExpiredStartNext =
        () -> {
          if (transitionedToNext.compareAndSet(false, true)) {
            final UInt64 currentSlot = getCurrentSlot();
            final UInt64 nextSlotStart = getSlotStartTimeMillis(currentSlot.increment());
            activateAdapter(scheduledAdapters, index + 1, nextSlotStart);
          }
        };
    adapter.adapter().scheduleDuties(startFrom, adapter.expiryMillis(), onExpiredStartNext);
  }

  private UInt64 getCurrentSlot() {
    return spec.getCurrentSlotFromTimeMillis(timeProvider.getTimeInMillis(), genesisTimeMillis);
  }

  private UInt64 getSlotStartTimeMillis(final UInt64 slot) {
    return spec.computeTimeMillisAtSlot(slot, genesisTimeMillis);
  }
}
