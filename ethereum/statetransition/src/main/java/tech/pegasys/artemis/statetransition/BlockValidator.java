package tech.pegasys.artemis.statetransition;

import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;

public interface BlockValidator {

  class ValidationResult {

  }

  ValidationResult validate(BeaconState state, BeaconBlock block);

}
