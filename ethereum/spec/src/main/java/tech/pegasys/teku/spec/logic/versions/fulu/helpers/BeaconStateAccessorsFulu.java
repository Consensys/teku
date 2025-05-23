package tech.pegasys.teku.spec.logic.versions.fulu.helpers;

import it.unimi.dsi.fastutil.ints.IntList;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64List;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.fulu.BeaconStateFulu;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.BeaconStateAccessorsElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.MiscHelpersElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.PredicatesElectra;

import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.spec.logic.common.helpers.MathHelpers.uint64ToBytes;

public class BeaconStateAccessorsFulu extends BeaconStateAccessorsElectra {
    private final SpecConfigFulu configFulu;

    public BeaconStateAccessorsFulu(
            final SpecConfig config,
            final PredicatesElectra predicatesElectra,
            final MiscHelpersFulu miscHelpers) {
        super(SpecConfigFulu.required(config), predicatesElectra, miscHelpers);
        configFulu = config.toVersionFulu().orElseThrow();
    }

    @Override
    public int getBeaconProposerIndex(final BeaconState state, final UInt64 requestedSlot) {
        final int lookAheadIndex = state.getSlot().mod(configFulu.getSlotsPerEpoch()).intValue();
        return state.toVersionFulu().orElseThrow().getProposerLookahead().asListUnboxed().get(lookAheadIndex).intValue();
    }


    public List<Integer> getBeaconProposerIndices(final BeaconState state, final UInt64 epoch) {
        //validateStateCanCalculateProposerIndexAtEpoch(state, requestedSlot);
        final Bytes32 seed = Hash.sha256(getSeed(state, epoch, Domain.BEACON_PROPOSER));
        IntList indices = getActiveValidatorIndices(state, epoch);
        return miscHelpers.computeProposerIndices(state,epoch, seed, indices);

    }

    public static BeaconStateAccessorsFulu required(
            final BeaconStateAccessors beaconStateAccessors) {
        checkArgument(
                beaconStateAccessors instanceof BeaconStateAccessorsElectra,
                "Expected %s but it was %s",
                BeaconStateAccessorsFulu.class,
                beaconStateAccessors.getClass());
        return (BeaconStateAccessorsFulu) beaconStateAccessors;
    }

}
