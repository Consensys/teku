package tech.pegasys.teku.core.operationstatetransitionvalidators;

import com.google.common.primitives.UnsignedLong;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.state.BeaconState;

import javax.annotation.CheckReturnValue;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.lang.Math.toIntExact;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_current_epoch;
import static tech.pegasys.teku.datastructures.util.ValidatorsUtil.is_slashable_validator;

public class ProposerSlashingStateTransitionValidator {

  public Optional<ProposerSlashingInvalidReason> validateSlashing(
          final BeaconState state, final ProposerSlashing proposerSlashing) {
    final BeaconBlockHeader header1 = proposerSlashing.getHeader_1().getMessage();
    final BeaconBlockHeader header2 = proposerSlashing.getHeader_2().getMessage();
    return firstOf(
            () -> check(
                    header1.getSlot().equals(header2.getSlot()),
                    ProposerSlashingInvalidReason.HEADER_SLOTS_DIFFERENT),
            () -> check(
                    header1.getProposer_index().equals(header2.getProposer_index()),
                    ProposerSlashingInvalidReason.PROPOSER_INDICES_DIFFERENT),
            () -> check(
                    !Objects.equals(proposerSlashing.getHeader_1(), proposerSlashing.getHeader_2()),
                    ProposerSlashingInvalidReason.SAME_HEADER),
            () -> check(
                    UnsignedLong.valueOf(state.getValidators().size())
                            .compareTo(header1.getProposer_index())
                            > 0,
                    ProposerSlashingInvalidReason.INVALID_PROPOSER),
            () -> check(
                    is_slashable_validator(
                            state.getValidators().get(toIntExact(header1.getProposer_index().longValue())),
                            get_current_epoch(state)
                    ),
                    ProposerSlashingInvalidReason.PROPOSER_NOT_SLASHABLE)
    );
  }

  @SafeVarargs
  private Optional<ProposerSlashingInvalidReason> firstOf(
          final Supplier<Optional<ProposerSlashingInvalidReason>>... checks) {
    return Stream.of(checks)
            .map(Supplier::get)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .findFirst();
  }

  @CheckReturnValue
  private Optional<ProposerSlashingInvalidReason> check(final boolean isValid, final ProposerSlashingInvalidReason check) {
    return !isValid ? Optional.of(check) : Optional.empty();
  }

  public enum ProposerSlashingInvalidReason {
      HEADER_SLOTS_DIFFERENT("Header slots don't match"),
      PROPOSER_INDICES_DIFFERENT("Header proposer indices don't match"),
      SAME_HEADER("Headers are not different"),
      INVALID_PROPOSER("Invalid proposer index"),
      PROPOSER_NOT_SLASHABLE("Proposer is not slashable");

      private final String description;

      ProposerSlashingInvalidReason(final String description) {
        this.description = description;
      }

      public String describe() {
        return description;
      }
    }
}
