package net.consensys.beaconchain.datastructures.state;

import net.consensys.beaconchain.datastructures.blocks.AttestationRecord;
import net.consensys.beaconchain.ethereum.core.Hash;

public class BeaconState {

  private Uint64 validator_set_change_slot;
  private ValidatorRecord[] validators;
  private CrosslinkRecord[] crosslinks;
  private Uint64 last_state_recalculation_slot;
  private Uint64 last_finalized_slot;
  private Uint64 last_justified_slot;
  private Uint64 justified_streak;
  private ShardAndCommittee[][] shard_and_committee_for_slots;
  private Uint24[][] persistent_committees;
  private ShardReassignmentRecord[] persistent_committee_reassignments;
  private Hash next_shuffling_seed;
  private Uint64[] deposits_penalized_in_period;
  private Hash validator_set_delta_hash_chain;
  private Uint64 current_exit_seq;
  private Uint64 genesis_time;
  private Hash processed_pow_receipt_root;
  private CandidatePoWReceiptRootRecord[] candidate_pow_receipt_roots;
  private Uint64 pre_fork_version;
  private Uint64 post_fork_version;
  private Uint64 fork_slot_number;
  private AttestationRecord[] pending_attestations;
  private Hash[] recent_block_hashes;
  private Hash randao_mix;

  public BeaconState() {

  }

}
