package tech.pegasys.artemis.statetransition.util;

import com.google.common.primitives.UnsignedLong;
import tech.pegasys.artemis.datastructures.state.Validators;
import tech.pegasys.artemis.statetransition.BeaconState;

public class CrosslinkCommitteeUtil {
    //Return the number of committees in the previous epoch of the given ``state`
    public static int get_previous_epoch_committee_count(BeaconState state){
        Validators previous_active_validators = ValidatorsUtil.get_active_validators(state.getValidator_registry(), state.getPrevious_calculation_epoch());
        return previous_active_validators.size();
    }
    //Return the number of committees in the previous epoch of the given ``state`
    public static int get_current_epoch_committee_count(BeaconState state){
        Validators previous_active_validators = ValidatorsUtil.get_active_validators(state.getValidator_registry(), state.getCurrent_calculation_epoch());
        return previous_active_validators.size();
    }

    public static int get_next_epoch_committee_count(BeaconState state) {
        Validators previous_active_validators = ValidatorsUtil.get_active_validators(state.getValidator_registry(), state.getCurrent_calculation_epoch().plus(UnsignedLong.ONE));
        return previous_active_validators.size();
    }
}
