package tech.pegasys.artemis.validator.client;

import com.google.common.primitives.UnsignedLong;

import java.util.List;

public class CommitteeAssignment {

  private List<Integer> committee;
  private UnsignedLong committeeIndex;
  private UnsignedLong slot;

  public CommitteeAssignment(List<Integer> committee,
                             UnsignedLong committeeIndex,
                             UnsignedLong slot) {
    this.committee = committee;
    this.committeeIndex = committeeIndex;
    this.slot = slot;
  }

  public List<Integer> getCommittee() {
    return committee;
  }

  public UnsignedLong getCommitteeIndex() {
    return committeeIndex;
  }

  public UnsignedLong getSlot() {
    return slot;
  }
}
