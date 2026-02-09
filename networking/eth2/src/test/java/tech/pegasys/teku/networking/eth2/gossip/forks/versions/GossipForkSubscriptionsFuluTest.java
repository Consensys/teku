package tech.pegasys.teku.networking.eth2.gossip.forks.versions;

import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.events.ChannelExceptionHandler;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.networking.eth2.P2PConfig;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.statetransition.CustodyGroupCountChannel;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class GossipForkSubscriptionsFuluTest {

    private static final int NUMBER_OF_CUSTODY_GROUPS = 128;

    private final Spec spec = TestSpecFactory.createMainnetFulu();
    private final EventChannels eventChannels =
            EventChannels.createSyncChannels(
                    new ChannelExceptionHandler() {
                        @Override
                        public void handleException(final Throwable error, final Object subscriber, final Method invokedMethod, final Object[] args) {

                        }
                    }, new NoOpMetricsSystem());

    private GossipForkSubscriptionsFulu gossipForkSubscriptionsFulu;
    private CustodyGroupCountChannel custodyGroupCountChannel;

    @BeforeEach
    void setUp() {
        assertThat(
                SpecConfigFulu.required(spec.forMilestone(SpecMilestone.FULU).getConfig())
                        .getNumberOfCustodyGroups())
                .isEqualTo(NUMBER_OF_CUSTODY_GROUPS);

        gossipForkSubscriptionsFulu =
                createGossipForkSubscriptionsFulu();

        eventChannels.subscribe(CustodyGroupCountChannel.class, gossipForkSubscriptionsFulu.getCustodyGroupCountSubscriber());
        custodyGroupCountChannel =
                eventChannels.getPublisher(CustodyGroupCountChannel.class);
    }

    @Test
    void isSuperNode_shouldReturnFalse_whenNoUpdateReceived() {
        assertThat(gossipForkSubscriptionsFulu.isSuperNode()).isFalse();
    }

    @Test
    void isSuperNode_shouldReturnFalse_whenCustodyGroupCountBelowThreshold() {
        custodyGroupCountChannel.onGroupCountUpdate(64, 0);

        assertThat(gossipForkSubscriptionsFulu.isSuperNode()).isFalse();
    }

    @Test
    void isSuperNode_shouldReturnFalse_whenCustodyGroupCountOneBelow() {
        custodyGroupCountChannel.onGroupCountUpdate(NUMBER_OF_CUSTODY_GROUPS - 1, 0);

        assertThat(gossipForkSubscriptionsFulu.isSuperNode()).isFalse();
    }

    @Test
    void isSuperNode_shouldReturnTrue_whenCustodyGroupCountEqualsTotal() {
        custodyGroupCountChannel.onGroupCountUpdate(NUMBER_OF_CUSTODY_GROUPS, 0);

        assertThat(gossipForkSubscriptionsFulu.isSuperNode()).isTrue();
    }

    @Test
    void isSuperNode_shouldReflectLatestUpdate() {
        custodyGroupCountChannel.onGroupCountUpdate(32, 0);
        assertThat(gossipForkSubscriptionsFulu.isSuperNode()).isFalse();

        custodyGroupCountChannel.onGroupCountUpdate(NUMBER_OF_CUSTODY_GROUPS, 0);
        assertThat(gossipForkSubscriptionsFulu.isSuperNode()).isTrue();

        custodyGroupCountChannel.onGroupCountUpdate(64, 0);
        assertThat(gossipForkSubscriptionsFulu.isSuperNode()).isFalse();
    }

    @Test
    void isSuperNode_shouldOnlyUseCustodyGroupCount_notSamplingGroupCount() {
        custodyGroupCountChannel.onGroupCountUpdate(1, NUMBER_OF_CUSTODY_GROUPS);

        assertThat(gossipForkSubscriptionsFulu.isSuperNode()).isFalse();
    }

    private GossipForkSubscriptionsFulu createGossipForkSubscriptionsFulu() {
        // Build using the real constructor or builder with
        // mocked/stubbed dependencies as needed
        return new GossipForkSubscriptionsFulu(
                spec.getForkSchedule().getFork(SpecMilestone.FULU),
                spec,
                null, // asyncRunner
                null, // metricsSystem
                null, // discoveryNetwork
                null, // recentChainData
                null, // gossipEncoding
                null, // blockProcessor
                null, // blobSidecarProcessor
                null, // attestationProcessor
                null, // aggregateProcessor
                null, // attesterSlashingProcessor
                null, // proposerSlashingProcessor
                null, // voluntaryExitProcessor
                null, // signedContributionAndProofOperationProcessor
                null, // syncCommitteeMessageOperationProcessor
                null, // signedBlsToExecutionChangeOperationProcessor
                null, // debugDataDumper
                null, // executionProofOperationProcessor
                null, // dasGossipLogger
                null,
                P2PConfig.builder().specProvider(spec).build() // p2pConfig
        );
    }
}