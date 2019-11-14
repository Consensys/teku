package tech.pegasys.artemis.validator.coordinator;

import com.google.common.primitives.UnsignedLong;

import java.util.List;

public class AttestationAssignment {

  private final List<Integer> committee;
  private final UnsignedLong committeIndex;
  private final int validatorIndex;
  private final boolean isAggregator;

  AttestationAssignment(
          List<Integer> committee,
          UnsignedLong committeIndex,
          int validatorIndex,
          boolean isAggregator) {
    this.committee = committee;
    this.committeIndex = committeIndex;
    this.validatorIndex = validatorIndex;
    this.isAggregator = isAggregator;
  }

  public List<Integer> getCommittee() {
    return committee;
  }

  public UnsignedLong getCommitteIndex() {
    return committeIndex;
  }

  public int getValidatorIndex() {
    return validatorIndex;
  }

  public boolean isAggregator() {
    return isAggregator;
  }
}
