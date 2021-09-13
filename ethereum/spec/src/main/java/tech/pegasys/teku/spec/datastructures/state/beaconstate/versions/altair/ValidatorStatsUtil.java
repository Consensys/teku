package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair;

import tech.pegasys.teku.spec.constants.ParticipationFlags;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.analysis.ValidatorStats.CorrectAndLiveValidators;
import tech.pegasys.teku.ssz.SszList;
import tech.pegasys.teku.ssz.primitive.SszByte;

public class ValidatorStatsUtil {

  public static CorrectAndLiveValidators getValidatorStats(
      final SszList<SszByte> participationFlags) {
    int numberOfCorrectValidators = 0;
    int numberOfLiveValidators = 0;
    for (SszByte participationFlag : participationFlags) {
      final byte flag = participationFlag.get();
      if (ParticipationFlags.isTimelyTarget(flag)) {
        numberOfCorrectValidators++;
      }
      if (ParticipationFlags.isAnyFlagSet(flag)) {
        numberOfLiveValidators++;
      }
    }
    return new CorrectAndLiveValidators(numberOfCorrectValidators, numberOfLiveValidators);
  }
}
