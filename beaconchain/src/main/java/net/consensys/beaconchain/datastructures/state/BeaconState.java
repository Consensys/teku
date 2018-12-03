package net.consensys.beaconchain.datastructures.state;

import net.consensys.beaconchain.datastructures.blocks.AttestationRecord;
import net.consensys.beaconchain.ethereum.core.Hash;

public class BeaconState {

  // Validator registry
  private ValidatorRecord[] validator_registry;
  private Uint64 validator_registry_latest_change_slot;
  private Uint64 validator_registry_exit_count;
  private Hash validator_registry_delta_chain_tip;

  // Randomness and committees
  private Hash randao_mix;
  private Hash next_seed;
  private ShardAndCommittee[][] shard_and_committee_for_slots;
  private Uint24[][] persistent_committees;
  private ShardReassignmentRecord[] persistent_committee_reassignments;

  // Finality
  private Uint64 previous_justified_slot;
  private Uint64 justified_slot;
  private Uint64 justified_slot_bitfield;
  private Uint64 finalized_slot;

  // Recent state
  private CrosslinkRecord[] latest_crosslinks;
  private Uint64 latest_state_recalculation_slot;
  private Hash[] latest_block_hashes;
  private Uint64[] latest_penalized_exit_balances;
  private PendingAttestationRecord[] latest_attestations;

  // PoW receipt root
  private Hash processed_pow_receipt_root;
  private CandidatePoWReceiptRootRecord[] candidate_pow_receipt_roots;

  // Misc
  private Uint64 genesis_time;
  private ForkData fork_data;

  public BeaconState() {

  }

}
