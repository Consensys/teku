# The Beacon Chain - Phase 0

## Ethereum 2.0 Overview

![1]

## High-level Architectural Mental Model

![2]

The beacon chain’s proposed architecture can be described using terminology similar to the Pantheon client:

- Attestations Pool: Manages pending attestations with peers.
- Synchronizer: Updates local blockchain based on peers.
- Block Processor: creates crosslinks, applies penalties and rewards.
- Blockchain: Manages blockchain constructs.
- Main Loop: Orchestrates interactions amongst components.
- JSON-RPC: Responsible for handling JSON-RPC messages.
- Logging, Configuration, CLI: Self-explanatory.
- P2P Communications
- Discovery - find peers/resources on the network
- Routing - what path (through peers) to take when communicating
- Transport - the communication protocol used
- Messaging - is transport agnostic and represents the way peers communicate at the application layer.  The current plan is to use GossipSub.

## Useful Links

Our Beacon Chain repo:

https://github.com/PegaSysEng/artemis

Ethereum 2.0 spec repo: 

https://github.com/ethereum/eth2.0-specs

Beacon Chain spec:

https://github.com/ethereum/eth2.0-specs/blob/master/specs/core/0_beacon-chain.md

Shard Chain spec: 

https://github.com/ethereum/eth2.0-specs/blob/master/specs/core/1_shard-data-chains.md


[1]: https://lh3.googleusercontent.com/StqAtpKiDwwTN3sWPT2dE-HtsArBDMtUw3IleW6jnzOz-ltDxr53GWzjMbBdsDCRJJwJjnBGVJo4n1T4yDUBRXLT5mJPWF3QrkKAc05b

[2]: https://lh5.googleusercontent.com/-QH_xFUh87_NYDHAWNB2OalCWQ9EWjAVO_YTcP3GhtEvAjCuVI-VAiekYt-ehFIj76AG4yPpZ0sw1O5jK2XQ1OjQvz5C3Y_pfGyQcqPM
