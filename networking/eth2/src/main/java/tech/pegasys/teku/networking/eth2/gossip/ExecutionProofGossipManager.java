package tech.pegasys.teku.networking.eth2.gossip;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationMilestoneValidator;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.Eth2TopicHandler;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.networking.p2p.gossip.TopicChannel;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecarSchema;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionProof;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionProofSchema;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.storage.client.RecentChainData;

import java.util.Optional;
import java.util.stream.IntStream;

import static tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopicName.getBlobSidecarSubnetTopicName;
import static tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopicName.getExecutionProofSubnetTopicName;
import static tech.pegasys.teku.spec.config.Constants.MAX_EXECUTION_PROOF_SUBNETS;

public class ExecutionProofGossipManager implements GossipManager{

    private static final Logger LOG = LogManager.getLogger();

    private final Spec spec;
    private final GossipNetwork gossipNetwork;
    private final GossipEncoding gossipEncoding;
    private final Int2ObjectMap<Eth2TopicHandler<ExecutionProof>> subnetIdToTopicHandler;
    private final GossipFailureLogger gossipFailureLogger;

    private final Int2ObjectMap<TopicChannel> subnetIdToChannel = new Int2ObjectOpenHashMap<>();

    private ExecutionProofGossipManager(final Spec spec, final GossipNetwork gossipNetwork, final GossipEncoding gossipEncoding, final Int2ObjectMap<Eth2TopicHandler<ExecutionProof>> subnetIdToTopicHandler, final GossipFailureLogger executionProof) {
        this.spec = spec;
        this.gossipNetwork = gossipNetwork;
        this.gossipEncoding = gossipEncoding;
        this.subnetIdToTopicHandler = subnetIdToTopicHandler;
        this.gossipFailureLogger = executionProof;
    }


    public static ExecutionProofGossipManager create(
            final RecentChainData recentChainData,
            final Spec spec,
            final AsyncRunner asyncRunner,
            final GossipNetwork gossipNetwork,
            final GossipEncoding gossipEncoding,
            final ForkInfo forkInfo,
            final Bytes4 forkDigest,
            final OperationProcessor<ExecutionProof> processor,
            final DebugDataDumper debugDataDumper) {
        final SpecVersion forkSpecVersion = spec.atEpoch(forkInfo.getFork().getEpoch());
        final ExecutionProofSchema gossipType =
                SchemaDefinitionsElectra.required(forkSpecVersion.getSchemaDefinitions())
                        .getExecutionProofSchema();
        final Int2ObjectMap<Eth2TopicHandler<ExecutionProof>> subnetIdToTopicHandler =
                new Int2ObjectOpenHashMap<>();
        final int executionProofSubnetCount = MAX_EXECUTION_PROOF_SUBNETS.intValue(); // Define this constant as per your configuration


        IntStream.range(0, executionProofSubnetCount)
                .forEach(
                        subnetId -> {
                            final Eth2TopicHandler<ExecutionProof> topicHandler =
                                    createExecutionProofTopicHandler(
                                            subnetId,
                                            recentChainData,
                                            spec,
                                            asyncRunner,
                                            processor,
                                            gossipEncoding,
                                            forkInfo,
                                            forkDigest,
                                            gossipType,
                                            debugDataDumper);
                            subnetIdToTopicHandler.put(subnetId, topicHandler);
                        });
        return new ExecutionProofGossipManager(
                spec,
                gossipNetwork,
                gossipEncoding,
                subnetIdToTopicHandler,
                GossipFailureLogger.createNonSuppressing("execution_proof"));
    }

    private static Eth2TopicHandler<ExecutionProof> createExecutionProofTopicHandler(
            final int subnetId,
            final RecentChainData recentChainData,
            final Spec spec,
            final AsyncRunner asyncRunner,
            final OperationProcessor<ExecutionProof> operationProcessor,
            final GossipEncoding gossipEncoding,
            final ForkInfo forkInfo,
            final Bytes4 forkDigest,
            final ExecutionProofSchema executionProofSchema,
            final DebugDataDumper debugDataDumper) {
        return new Eth2TopicHandler<>(
                recentChainData,
                asyncRunner,
                operationProcessor,
                gossipEncoding,
                forkDigest,
                getExecutionProofSubnetTopicName(subnetId),
                new OperationMilestoneValidator<>(
                        recentChainData.getSpec(),
                        forkInfo.getFork(),
                        __ -> spec.computeEpochAtSlot(recentChainData.getHeadSlot())),
                executionProofSchema,
                recentChainData.getSpec().getNetworkingConfig(),
                debugDataDumper);
    }

    @Override
    public void subscribe() {
        subnetIdToTopicHandler
                .int2ObjectEntrySet()
                .forEach(
                        entry -> {
                            final Eth2TopicHandler<ExecutionProof> topicHandler = entry.getValue();
                            final TopicChannel channel =
                                    gossipNetwork.subscribe(topicHandler.getTopic(), topicHandler);
                            subnetIdToChannel.put(entry.getIntKey(), channel);
                        });
    }

    @Override
    public void unsubscribe() {
        subnetIdToChannel.values().forEach(TopicChannel::close);
        subnetIdToChannel.clear();
    }

    @Override
    public boolean isEnabledDuringOptimisticSync() {
        return true;
    }

    /**
     * This method is designed to return a future that only completes successfully whenever the gossip
     * was succeeded (sent to at least one peer) or failed.
     */
    public SafeFuture<Void> publishExecutionProof(final ExecutionProof message) {
        final int subnetId = spec.computeSubnetForExecutionProof(message).intValue();
        return Optional.ofNullable(subnetIdToChannel.get(subnetId))
                .map(channel -> channel.gossip(gossipEncoding.encode(message)))
                .orElse(SafeFuture.failedFuture(new IllegalStateException("Gossip channel not available")))
                .handle(
                        (__, err) -> {
                            if (err != null) {
                                gossipFailureLogger.log(err, Optional.empty());
                            } else {
                                LOG.info(
                                        "Successfully gossiped executionProof {} on {}",
                                        () -> message,
                                        () -> subnetIdToTopicHandler.get(subnetId).getTopic());
                            }
                            return null;
                        });
    }
}
