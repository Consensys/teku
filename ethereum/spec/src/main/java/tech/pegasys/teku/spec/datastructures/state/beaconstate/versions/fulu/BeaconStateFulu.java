package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.fulu;

import com.google.common.base.MoreObjects;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.MutableBeaconStateElectra;

import java.util.Optional;

public interface BeaconStateFulu extends BeaconStateElectra {
    static BeaconStateFulu required(final BeaconState state) {
        return state
                .toVersionFulu()
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Expected an Electra state but got: " + state.getClass().getSimpleName()));
    }

    private static <T extends SszData> void addItems(
            final MoreObjects.ToStringHelper stringBuilder,
            final String keyPrefix,
            final SszList<T> items) {
        for (int i = 0; i < items.size(); i++) {
            stringBuilder.add(keyPrefix + "[" + i + "]", items.get(i));
        }
    }

    static void describeCustomFuluFields(
            final MoreObjects.ToStringHelper stringBuilder, final BeaconStateFulu state) {
        BeaconStateFulu.describeCustomFuluFields(stringBuilder, state);
        addItems(stringBuilder, "pending_deposits", state.getPendingDeposits());
    }

    @Override
    MutableBeaconStateFulu createWritableCopy();

    default <E1 extends Exception, E2 extends Exception, E3 extends Exception>
    BeaconStateFulu updatedFulu(
            final Mutator<MutableBeaconStateFulu, E1, E2, E3> mutator) throws E1, E2, E3 {
        MutableBeaconStateFulu writableCopy = createWritableCopy();
        mutator.mutate(writableCopy);
        return writableCopy.commitChanges();
    }

    @Override
    default Optional<BeaconStateFulu> toVersionFulu() {
        return Optional.of(this);
    }
}
