package tech.pegasys.artemis.validatorclient;

import java.util.List;

public class CommitteeAssignmentTuple {

  private List<Integer> validators;
  private int shard;
  private int slot;
  private boolean bool;

  CommitteeAssignmentTuple(List<Integer> validators, int shard, int slot, boolean bool) {
    this.validators = validators;
    this.shard = shard;
    this.slot = slot;
    this.bool = bool;
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public List<Integer> getValidators() {
    return validators;
  }

  public int getShard() {
    return shard;
  }


  public int getSlot() {
    return slot;
  }

  public boolean isBool() {
    return bool;
  }

}
