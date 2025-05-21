package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.fulu;

import org.apache.tuweni.units.bigints.UInt64;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64List;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.MutableBeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingPartialWithdrawal;

import java.util.Optional;

import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields.PENDING_PARTIAL_WITHDRAWALS;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields.PROPOSER_LOOKAHEAD;

public interface MutableBeaconStateFulu extends MutableBeaconStateElectra, BeaconStateFulu {
    static MutableBeaconStateFulu required(final MutableBeaconState state) {
        return state
                .toMutableVersionFulu()
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Expected an Electra state but got: " + state.getClass().getSimpleName()));
    }

    @Override
    BeaconStateFulu commitChanges();

    @Override
    default Optional<MutableBeaconStateFulu> toMutableVersionFulu() {
        return Optional.of(this);
    }

    default void setProposerLookahead(
            final SszUInt64List proposerLookahead) {
        final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.PROPOSER_LOOKAHEAD);
        set(fieldIndex, proposerLookahead);
    }

    @Override
    default SszMutableList<SszUInt64> getProposerLookahead() {
        final int index = getSchema().getFieldIndex(PROPOSER_LOOKAHEAD);
        return getAnyByRef(index);
    }

}
