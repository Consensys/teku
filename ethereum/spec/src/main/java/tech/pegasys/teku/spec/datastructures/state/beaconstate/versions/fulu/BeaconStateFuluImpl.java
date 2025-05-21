package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.fulu;

import com.google.common.base.MoreObjects;
import tech.pegasys.teku.infrastructure.ssz.SszContainer;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.cache.IntCache;
import tech.pegasys.teku.infrastructure.ssz.schema.SszCompositeSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.AbstractBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.SlotCaches;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.TransitionCaches;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.ValidatorStatsAltair;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.fulu.BeaconStateFulu;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.fulu.BeaconStateSchemaFulu;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.fulu.MutableBeaconStateFulu;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.fulu.MutableBeaconStateFuluImpl;

public class BeaconStateFuluImpl extends AbstractBeaconState<MutableBeaconStateFulu>
        implements BeaconStateFulu, BeaconStateCache, ValidatorStatsAltair {

    BeaconStateFuluImpl(
            final BeaconStateSchema<BeaconStateFulu, MutableBeaconStateFulu> schema) {
        super(schema);
    }

    BeaconStateFuluImpl(
            final SszCompositeSchema<?> type,
            final TreeNode backingNode,
            final IntCache<SszData> cache,
            final TransitionCaches transitionCaches,
            final SlotCaches slotCaches) {
        super(type, backingNode, cache, transitionCaches, slotCaches);
    }

    BeaconStateFuluImpl(
            final AbstractSszContainerSchema<? extends SszContainer> type, final TreeNode backingNode) {
        super(type, backingNode);
    }

    @Override
    public BeaconStateSchemaFulu getBeaconStateSchema() {
        return (BeaconStateSchemaFulu) getSchema();
    }

    @Override
    public MutableBeaconStateFulu createWritableCopy() {
        return new MutableBeaconStateFuluImpl(this);
    }

    @Override
    protected void describeCustomFields(final MoreObjects.ToStringHelper stringBuilder) {
        BeaconStateFulu.describeCustomFuluFields(stringBuilder, this);
    }
}
