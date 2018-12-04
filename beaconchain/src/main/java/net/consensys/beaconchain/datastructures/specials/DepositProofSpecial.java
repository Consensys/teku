package net.consensys.beaconchain.datastructures.specials;

import net.consensys.beaconchain.ethereum.core.Hash;

public class DepositProofSpecial {

  private Hash[] merkle_branch;
  private Uint64 merkle_tree_index;

  //     # Deposit data
  //    'deposit_data': {
  //        # Deposit parameters
  //        'deposit_parameters': DepositParametersRecord,
  //        # Value in Gwei
  //        'value': 'uint64',
  //        # Timestamp from deposit contract
  //        'timestamp': 'uint64',
  //    },

  public DepositProofSpecial() {

  }

}
