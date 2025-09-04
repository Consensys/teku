package tech.pegasys.teku.networking.eth2.gossip.subnets;

import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.storage.client.RecentChainData;

import static tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopics.getExecutionProofSubnetTopic;

public class ExecutionProofSubnetTopicProvider {
    private final RecentChainData recentChainData;
    private final GossipEncoding gossipEncoding;

    public ExecutionProofSubnetTopicProvider(
            final RecentChainData recentChainData, final GossipEncoding gossipEncoding) {
        this.recentChainData = recentChainData;
        this.gossipEncoding = gossipEncoding;
    }

    public String getTopicForSubnet(final int subnetId) {
        final Bytes4 forkDigest = recentChainData.getCurrentForkDigest().orElseThrow();
        return getExecutionProofSubnetTopic(forkDigest, subnetId, gossipEncoding);
    }
}
