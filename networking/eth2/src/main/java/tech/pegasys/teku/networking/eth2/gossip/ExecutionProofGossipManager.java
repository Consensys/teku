package tech.pegasys.teku.networking.eth2.gossip;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.subnets.ExecutionProofSubnetSubscriptions;
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

    private final ExecutionProofSubnetSubscriptions executionProofSubnetSubscriptions;

    private final Int2ObjectMap<TopicChannel> subnetIdToChannel = new Int2ObjectOpenHashMap<>();

    public ExecutionProofGossipManager(final ExecutionProofSubnetSubscriptions executionProofSubnetSubscriptions) {
        this.executionProofSubnetSubscriptions = executionProofSubnetSubscriptions;
    }

    @Override
    public void subscribe() {
        executionProofSubnetSubscriptions.subscribe();
    }

    @Override
    public void unsubscribe() {
       executionProofSubnetSubscriptions.unsubscribe();
    }

    @Override
    public boolean isEnabledDuringOptimisticSync() {
        return true;
    }

    public void subscribeToSubnetId(final int subnetId) {
        executionProofSubnetSubscriptions.subscribeToSubnetId(subnetId);
    }

    public void unsubscribeFromSubnetId(final int subnetId) {
        executionProofSubnetSubscriptions.unsubscribeFromSubnetId(subnetId);
    }

    public void publish(final ExecutionProof executionProof){
        executionProofSubnetSubscriptions.gossip(executionProof);
    }

}
