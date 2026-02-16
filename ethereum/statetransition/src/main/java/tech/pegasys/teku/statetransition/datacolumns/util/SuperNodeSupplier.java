package tech.pegasys.teku.statetransition.datacolumns.util;

import com.google.common.base.Supplier;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.statetransition.datacolumns.CustodyGroupCountManager;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class SuperNodeSupplier implements Supplier<Boolean> {


    private final Spec spec;
    private final boolean isSubscribedToAllCustodySubnetsEnabled;
    private final CustodyGroupCountManager custodyGroupCountManager;

    public SuperNodeSupplier(final Spec spec, final boolean isSubscribedToAllCustodySubnetsEnabled, final CustodyGroupCountManager custodyGroupCountManager) {
        this.spec = spec;
        this.isSubscribedToAllCustodySubnetsEnabled = isSubscribedToAllCustodySubnetsEnabled;
        this.custodyGroupCountManager = custodyGroupCountManager;
    }

    @Override
    public Boolean get() {
        if (!spec.isMilestoneSupported(SpecMilestone.FULU)) {
            return false;
        }
        if (isSubscribedToAllCustodySubnetsEnabled) {
            return true;
        }
        return MiscHelpersFulu.required(
                        spec.forMilestone(SpecMilestone.FULU).miscHelpers())
                .isSuperNode(custodyGroupCountManager.getCustodyGroupCount());
    }
}
