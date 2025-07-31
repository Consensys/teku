package tech.pegasys.teku.storage.server.pruner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.metrics.SettableLabelledGauge;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.statetransition.datacolumns.DasCustodyStand;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DataColumnSidecarPrunerTest {
    public static final Duration PRUNE_INTERVAL = Duration.ofSeconds(5);
    public static final int PRUNE_LIMIT = 10;

    private final Spec spec = TestSpecFactory.createMinimalFulu();
    private final int slotsPerEpoch = spec.getGenesisSpecConfig().getSlotsPerEpoch();
    private final int secondsPerSlot = spec.getGenesisSpecConfig().getSecondsPerSlot();

    private final UInt64 genesisTime = UInt64.valueOf(0);

    private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(0);
    private final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);
    private final Database database = mock(Database.class);
    private final StubMetricsSystem stubMetricsSystem = new StubMetricsSystem();

    private final DataColumnSidecarPruner dataColumnSidecarPruner =
            new DataColumnSidecarPruner(
                    spec,
                    database,
                    stubMetricsSystem,
                    asyncRunner,
                    timeProvider,
                    PRUNE_INTERVAL,
                    PRUNE_LIMIT,
                    false,
                    "test",
                    mock(SettableLabelledGauge.class),
                    mock(SettableLabelledGauge.class));

    @BeforeEach
    void setUp() {
        when(database.getGenesisTime()).thenReturn(Optional.of(genesisTime));
        assertThat(dataColumnSidecarPruner.start()).isCompleted();
    }

    @Test
    void shouldNotPruneWhenGenesisNotAvailable() {
        when(database.getGenesisTime()).thenReturn(Optional.empty());

        asyncRunner.executeDueActions();

        verify(database).getGenesisTime();
        verify(database, never()).pruneAllSidecars(any(), anyInt());
    }

    @Test
    void shouldNotPrunePriorGenesis() {
        asyncRunner.executeDueActions();

        verify(database).getGenesisTime();
        verify(database, never()).pruneAllSidecars(any(), anyInt());

    }

    @Test
    void checkCalculatedMinSlotMatchesDALastSlotOfMinEpoch() {
        final SpecConfig config = spec.forMilestone(SpecMilestone.FULU).getConfig();
        final SpecConfigFulu specConfigFulu = SpecConfigFulu.required(config);
        final UInt64 minEpochsForDataColumnSidecarsRequests =
                UInt64.valueOf(specConfigFulu.getMinEpochsForDataColumnSidecarsRequests());

        final UInt64 currentSlot =
                UInt64.valueOf(specConfigFulu.getMinEpochsForDataColumnSidecarsRequests() + 1)
                        .times(slotsPerEpoch)
                        .plus(slotsPerEpoch / 2);
        final UInt64 currentTime = currentSlot.times(secondsPerSlot);

        final UInt64 currentEpoch =
                currentSlot.dividedBy(UInt64.valueOf(slotsPerEpoch));
        final UInt64 earliestEpochToKeep = currentEpoch.minus(minEpochsForDataColumnSidecarsRequests);
        final UInt64 earliestSlotToKeep =
                spec.computeStartSlotAtEpoch(earliestEpochToKeep).minusMinZero(1);
        timeProvider.advanceTimeBy(Duration.ofSeconds(currentTime.longValue()));

        asyncRunner.executeDueActions();
        verify(database)
                .pruneAllSidecars(earliestSlotToKeep, PRUNE_LIMIT);
    }



}
