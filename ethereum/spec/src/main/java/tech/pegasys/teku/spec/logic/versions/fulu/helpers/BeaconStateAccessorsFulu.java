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
import tech.pegasys.teku.spec.logic.versions.electra.helpers.BeaconStateAccessorsElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.MiscHelpersElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.PredicatesElectra;

import java.util.List;

import static tech.pegasys.teku.spec.logic.common.helpers.MathHelpers.uint64ToBytes;

public class BeaconStateAccessorsFulu extends BeaconStateAccessorsElectra {
    private final SpecConfigFulu configFulu;

    public BeaconStateAccessorsFulu(
            final SpecConfig config,
            final PredicatesElectra predicatesElectra,
            final MiscHelpersElectra miscHelpers) {
        super(SpecConfigFulu.required(config), predicatesElectra, miscHelpers);
        configFulu = config.toVersionFulu().orElseThrow();
    }


    public List<UInt64> getBeaconProposerIndex(final BeaconStateFulu state) {
        return state.getProposerLookahead().asListUnboxed();
    }

    public List<UInt64> computeProposerIndeces(final BeaconState state, final UInt64 requestedEpoch) {
        //validateStateCanCalculateProposerIndexAtSlot(state, requestedSlot); might be useful to add similar check later
        return BeaconStateCache.getTransitionCaches(state)
                .getBeaconProposerIndex()
                .get(
                        requestedEpoch,
                        slot -> {
                            final UInt64 startSlot = miscHelpers.computeStartSlotAtEpoch(requestedEpoch);
                            final Bytes32 epochSeed =
                                    Hash.sha256(getSeed(state, epoch, Domain.BEACON_PROPOSER), uint64ToBytes(slot));
                            IntList indices = getActiveValidatorIndices(state, epoch);
                            return miscHelpers.computeProposerIndex(state, indices, epochSeed);
                        });
    }
}
