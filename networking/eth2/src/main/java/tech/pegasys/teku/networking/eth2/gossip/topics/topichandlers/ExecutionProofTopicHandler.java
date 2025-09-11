package tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers;

import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationMilestoneValidator;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionProof;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionProofSchema;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.storage.client.RecentChainData;

public class ExecutionProofTopicHandler {

    public static Eth2TopicHandler<?> createHandler(
            final RecentChainData recentChainData,
            final AsyncRunner asyncRunner,
            final OperationProcessor<ExecutionProof> operationProcessor,
            final GossipEncoding gossipEncoding,
            final ForkInfo forkInfo,
            final Bytes4 forkDigest,
            final String topicName,
            final ExecutionProofSchema executionProofSchema,
            final DebugDataDumper debugDataDumper) {
        final Spec spec = recentChainData.getSpec();
        return new Eth2TopicHandler<>(
                recentChainData,
                asyncRunner,
                operationProcessor,
                gossipEncoding,
                forkDigest,
                topicName,
                new OperationMilestoneValidator<>(
                        recentChainData.getSpec(),
                        forkInfo.getFork(),
                        __ -> spec.computeEpochAtSlot(recentChainData.getHeadSlot())),
                executionProofSchema,
                recentChainData.getSpec().getNetworkingConfig(),
                debugDataDumper);

    }

}
