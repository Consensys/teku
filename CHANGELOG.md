# Changelog

## Upcoming Breaking Changes

- The default docker image will be updated to Java 15. Java 15 based images are available now for testing with the `-jdk15` suffix (e.g `consensys/teku:develop-jdk15`)
- Teku currently publishes a `head` event on the REST API 4 seconds into a slot even if a block has not been received. In a future release this will be changed so `head` event is only published when a new
  chain head block is imported. The `--Xvalidators-dependent-root-enabled` option can be used to switch to the new behaviour now for testing. 
  Note: this should be applied to both the beacon node and validator client if running separately.
- The `/teku/v1/beacon/states/:state_id` endpoint has been deprecated in favor of the standard API `/eth/v1/debug/beacon/states/:state_id` which now returns the state as SSZ when the `Accept: application/octet-stream` header is specified on the request.
- Docker images are now being published to `consensys/teku`. The `pegasys/teku` images will continue to be updated for the next few releases but please update your configuration to use `consensys/teku`.
- `--validators-key-files` and `--validators-key-password-files` have been replaced by `--validator-keys`. The old arguments will be removed in a future release.

## 21.1.0

### Additions and Improvements
- Added support for loading graffiti from a file to allow it to be changed without restarting. Thanks to [EdwardPrentice](https://github.com/EdwardPrentice)
- Supported retrieving states as SSZ on the standard REST API `/eth/v1/debug/beacon/states/:state_id` when the `Accept: application/octet-stream` header is specified in the request.
- Improved performance when verifying aggregate signatures.
- Added verification of block signatures when downloading historic blocks after a snapshot sync.

### Bug Fixes
- Fixed issue which could lead to the incorrect proposer index being calculated after a long period without network connectivity.
- Fixed issue where Teku did not exit at startup when validator keys were loaded.

## 20.12.1

### Additions and Improvements
- Add some performance optimizations to improve block inclusions rates and attestation accuracy.
- Improve deserialization performance for large data structures, speeding up deserialization up to 1000x for large beacon states.
- Build and publish docker images on OpenJDK 15 (which supports arm64) in addition to OpenJDK 14.
  Java 15 based images are available now for testing with the `-jdk15` suffix (e.g `consensys/teku:develop-jdk15`)
- Add periodic checks to make sure any configured external signer (`--validators-external-signer-url`) is reachable. 
  If the external signing service cannot be reached, errors are logged each time teku fails to contact the external signer.
- Limit the rate at which signature requests are sent to external signers at the start of each epoch.
- Add options to configure keystore and truststore for TLS communication and authentication with external signer.
- Add metrics to track validator participation (`previous_epoch_participation_weight`, `previous_epoch_total_weight`). 
  This enables accurate tracking of network participation rate.
- Add `sync_state` event type to events REST API endpoint.
- Improve logging and validation around `--initial-state` arguments.

### Bug Fixes
- Fix an issue where teku could produce aggregate attestations when it should not.
- Fix an issue with `--log-file-name-pattern` where teku would rotate log files to the default data directory even when a custom data directory was specified using `--data-path`.
- Break up requests for validator information into batches to avoid sending requests that are too large.
- Fix a bug in our peer selection logic so that we now select a subset of peers randomly in order to guard against
  eclipse attacks.
  
## 20.12.0

### Additions and Improvements

- Improved support for Nethermind and OpenEthereum when historic blocks are not fully available.
- Improved logging for Eth1 status to provide a warning when the node fails to respond to requests for deposit logs.
  Also includes a message to the console when all current deposit logs have been received.
- Maximum number of blocks per request for Eth1 deposit logs can now be configured with `--eth1-deposit-contract-max-request-size`.
- Added support for setting CORS headers in REST API to enable access from browser-based apps.
- `voluntary-exit` sumcommand now defaults to using the current epoch.


### Bug Fixes

- Improved help text for `--eth1-deposit-contract-address` to clarify it is not required for named networks.
- Reduced default number of blocks per request for eth_getLogs requests further.
- Fixed issue in external validator which could result in multiple requests for fork data from the beacon node at startup.
- Reduced memory usage of in memory beacon states.
- Fix `AbstractRouter internal error on message control` exception.
- Fix incorrect penalty applied to peers when requested gossip data was received from a different peer before the original peer's response.


## 20.11.1

### Bug Fixes
- Resolve slow lookup for validators by public key, particularly when the public key is not a known validator.
- Ensure correct source target is used when creating attestations for an empty slot at the start of the epoch.
- Fix `--initial-state` option so it is included in help output and support `--initial-state ""` to ignore built-in states and recalculate genesis from the Eth1 chain.
- Improved error message when Eth1 node fails to return data for a requested block.
- Reduced batch size when retrieving deposits from the Eth1 chain. Improves compatibility with Geth nodes with limited resources. 

## 20.11.0

The beacon chain is set to launch with the MainNet genesis state now set. The beacon chain will launch on 1 December 2020 at 12:00:23 UTC. 
Teku 20.11.0 is fully production ready and has full support for the beacon chain Mainnet.

### Breaking Changes

- REST API endpoint `/eth/v1/beacon/pool/attestations` now accepts an array of attestations instead of a single attestation.

### Additions and Improvements

- Full support for Eth2 MainNet. Genesis state and bootnodes are now configured as part of the `--network mainnet` option. 
- Voluntary exits and slashings submitted via the REST API are now broadcast to the gossip network.
- Added `/teku/v1/admin/liveness` endpoint which always returns 200 OK to be used to check if the REST API is live.
- Added `dependent_root` field to attester and block producer duties as well as 
  `current_duty_dependent_root` and `previous_duty_dependent_root` to the `head` events to aid validator clients in determining when duties are invalidated by changes in the chain.
- Tightened validation of data from the beacon node in the validator client to ensure blocks and attestations from far future slots are not signed. 

### Bug Fixes

- Fixed incorrect expected attestation metric.
- Fixed incorrect `beacon_previous_correct_validators` metrics.
- Fixed race condition during validator startup which could lead to duties not being performed for the first two epochs.
- Provide a human-friendly error message when the `--network` argument is unknown.
- Provide more human-friendly error messages when invalid options are given to the `voluntary-exit` subcommand.
- Avoided duplicate epoch processing during block creation.
- Reduce noise from `UNKNOWN_PARENT` errors when syncing.
- Drop discovery session when a malformed `authheadermessage` packet is received.

## 20.11.0-RC2

### Additions and Improvements

- Optimised gossip handling in jvm-libp2p.
- Optimised attestation and aggregate verification, ensuring signatures are only verified once.
- Added new metrics `beacon_current_correct_validators` and `beacon_previous_correct_validators` to report the number of validators that attested to the correct head block in the current and previous epochs.

### Bug Fixes

- Fixed seenTTL value in gossipsub configuration. Significantly reduces rate of gossip traffic. 
- Avoid potential errors when regenerating states immedately after starting from a non-genesis state.
- Fixed issue where validator duties may not be performed during the first two epochs after restart.

## 20.11.0-RC1

### Breaking Changes

- The default network is now `mainnet`. Use `--network medalla` to continue using the Medalla testnet.
- REST API endpoints have been migrated to the standard API and deprecated endpoints removed. Replacements:
  - GET `/eth/v1/beacon/states/{state_id}/validators/{validator_id}` replaced by POST `/eth/v1/beacon/states/{state_id}/validators/{validator_id}`
  - `/network/enr` replaced by `/eth/v1/node/identity`
  - `/network/listen_addresses` replaced by `/eth/v1/node/identity`
  - `/network/peer_id` replaced by `/eth/v1/node/identity`
  - `/network/peers` replaced by `/eth/v1/node/peers`
  - `/network/peer_count` replaced by `/eth/v1/node/peers`
  - `/network/listen_port` replaced by `/eth/v1/node/identity`
  - `/node/fork` replaced by `/eth/v1/beacon/states/{state_id}/fork`
  - `/node/genesis_time` replaced by `/eth/v1/beacon/genesis`
  - `/node/syncing` replaced by `/eth/v1/node/syncing`
  - `/node/version` replaced by `/eth/v1/node/version`
  - GET `/validator/attestation` replaced by `/eth/v1/validator/attestation_data`
  - POST `/validator/attestation` replaced by `/eth/v1/beacon/pool/attestations`
  - GET `/validator/block` replaced by `/eth/v1/validator/blocks/{slot}`
  - POST `/validator/block` replaced by `/eth/v1/beacon/blocks`
  - `/validator/aggregate_attestation` replaced by `/eth/v1/validator/aggregate_attestation`
  - POST `/validator/duties` replaced by POST `/eth/v1/validator/duties/attester/{epoch}` and GET `/eth/v1/validator/duties/proposer/{epoch}`
  - `/validator/aggregate_and_proofs` replaced by `/eth/v1/validator/aggregate_and_proofs`
  - `/validator/beacon_committee_subscription` replaced by `/eth/v1/validator/beacon_committee_subscriptions`
  - `/validator/persistent_subnets_subscription` deprecated. The beacon node now automatically establishes persistent subnet subscriptions based on calls to `/eth/v1/validator/beacon_committee_subscriptions`
  - `/beacon/block` deprecated. Replaced by `/eth/v1/beacon/blocks/{block_id}`
  - `/admin/log_level` moved to `/teku/v1/admin/log_level`
- Validator subcommands for generating and registering validators have been removed. Please use the Eth2 Launchpad to register validators, which is the most secure way of generating keys and sending deposits.
- Log files are now stored under the specified `--data-path` in `<data-path>/logs/teku.log`. The location is unchanged if `--data-path` is not specified.

### Additions and Improvements

- Switched default network to `mainnet`. Note that as the MainNet genesis state is not yet set, an `--eth1-endpoint` must be provided when using MainNet.
- Added support for the `pyrmont` testnet via `--network pyrmont`.
- Added `teku voluntary-exit` subcommand to allow creating and sending voluntary exit operations for validators.
- Syncing from a non-genesis state (weak subjectivity sync) is now fully supported. Use `--initial-state` to specify a finalized state to begin syncing from.
- Added two REST APIs to download states as SSZ, suitable for use with `--initial-state`.
  - By block root, slot or keyword (eg `finalized`): `/teku/v1/beacon/blocks/:block_id/state`
  - By state root, slot or keyword (eg `finalized`): `/teku/v1/beacon/states/:state_id`
- Provide clearer information in logs when external signers refuse to sign because of a potential slashing condition.
- Provide more detailed information when internal slashing protection refuses to sign because of a potential slashing condition.
- Allow built-in slashing protection to be disabled for external signers with `--validators-external-signer-slashing-protection-enabled=false`
- Added support for slashing protection interchange format v5.
- Historical blocks are now backfilled when syncing from a non-genesis state.

### Bug Fixes

- Updated jvm-libp2p to fix `CantDecryptInboundException` errors in logs.
- Reduced log level when malformed response is received from a remote peer during sync.
- Fixed incompatibility with the standard slashing protection interchange format.
- Fix OpenAPI specification to remove the invalid `exampleSetFlag` field.
- Fix response content for `/eth/v1/beacon/states/:state_id/finality_checkpoints` in OpenAPI specification.

## 0.12.14

### Breaking Changes
- In post data for `/eth/v1/validator/aggregate_and_proofs`, `index` has been renamed to `aggregator_index` and `attestation` has been renamed to `aggregate` reflecting the latest version of the standard API spec.
- Removed network definitions for `spadina` and `zinken` testnets which are no longer active.
- The `validator` subcommands to generate validator keys and send deposit transactions have been removed. Use the Eth2 Launchpad to register validators.

### Additions and Improvements
- Added the Mainnet network definition. To use Mainnet, ensure the ETH1 node is connected to Eth1 Mainnet and specify `--network mainnet`.
- New standard API endpoints:
  - GET `/eth/v1/config/fork_schedule`
  - GET `/eth/v1/beacon/blocks/{block_id}`
  - GET `/eth/v1/beacon/blocks/{block_id}/attestations`
  - GET `/eth/v1/beacon/blocks/{block_id}/root`
  - POST `/eth/v1/beacon/pool/voluntary_exits`
  - POST `/eth/v1/beacon/pool/proposer_slashings`
  - POST `/eth/v1/beacon/pool/attester_slashings`
  - GET `/eth/v1/config/deposit_contract`
- Added support for the Toledo devnet.
- Early access: Weak subjectivity sync no longer requires the initial block.  Only the state needs to be provided via `--Xws-initial-state`. Additionally the `Xws-initial-state` option can be specified via a config file.
- Support either plain text or JSON responses from external signers.
- Identify which validator was affected when validator duties fail.
- Removed support for uncompressed `SSZ` encoding for gossip and p2p RPC. All networks have been using `SSZ_SNAPPY` encoding for some time.

### Bug Fixes
- Fixed an issue where deposit events were retrieved from a range of blocks that was too large. This resulted in overloading local Geth nodes running on Mainnet, and exceeding the 10,000 log event limit in Infura responses.
- Reduce log level for `RpcTimeoutException` during sync.
- Check equality of the header in proposer slashings, not the signed wrapper. Fixes a potential state transition incompatibility found by fuzz testing.


## 0.12.13

### Breaking Changes
- `/eth/v1/beacon/{state}/committees` now specifies the epoch as a query parameter instead of a part of the path
- Discovery has been updated to discv5.1

### Additions and Improvements
- Reduced memory usage when finalizing after a long period of non-finalization and provided progress messages while finalized blocks are stored
- New standard API endpoints:
  - GET `/eth/v1/debug/beacon/heads`
- Updated discovery to use discv5.1 standard
- Significantly reduced CPU usage, particularly during long periods of non-finalization by optimising the `get_ancestor` method in fork choice
- Added `libp2p_gossip_messages_total` metric to report the number of gossip messages processed by gossip topic
- Apache license notice is now displayed on startup

### Bug Fixes
- Handled retrieving states for `finalized` or `head` when the actual slot is empty
- Fixed issue where EIP2335 keystores without a `path` or `uuid` field were rejected as invalid
- Fixed error codes returned when validators were not found in standard API queries
- Skip loading the latest finalized state on startup as it is not required to recreate the memory store
- Fixed handling of empty responses from peers when requesting a single block that they do not have


## 0.12.12

### Breaking Changes
- Docker images are now being published to `consensys/teku`. The `pegasys/teku` images will continue to be updated for the next few releases but please update your configuration to use `consensys/teku`.
- Support for the v3 database format has been removed.  This is an old format that was replaced prior to the Medalla testnet launching so all users should already be on more recent versions. 

### Additions and Improvements
- External signing API now includes all data required for the external signer to independently compute the signing root
- New standard REST API endpoints:
  - GET `/eth/v1/beacon/headers` - currently the `parent_root` option is not supported
  - GET `/eth/v1/debug/states/{state_id}`
  - GET `/eth/v1/config/spec`
- Validator client now supports basic authentication when the credentials are specified as part of the `--beacon-node-api-endpoint` option
- Validator client now has simple handling for 429 Too Many Request responses from the beacon node
- Added `--p2p-subscribe-all-subnets-enabled` option to the beacon node to automatically create persistent subscriptions to all attestation subnets
- Early access: support for starting sync from an arbitrary point in the chain with `--Xws-initial-state=<path-to-ssz-encoded-state> --Xws-initial-block=<path-to-ssz-encoded-block>`. As this is early access, the CLI options are subject to change.


### Bug Fixes
- Fixed a long stall when the chain finalizes after a long period of non-finalization in archive mode
- `--config-file` option can now be used with subcommands and specified either before or after the subcommand on the command line
- Fixed issue where validator committee indices were not string formatted in the standard REST API responses
- Requests for `head`, `justified` and `finalized` blocks would incorrectly return a `404` response if the slot was empty
- Fixed issue where the `slot` parameter for the `/eth/v1/validator/aggregate_attestation` endpoint was ignored and the validator client always set it to 0 when making requests.
- Fixed issue where the `/eth/v1/validator/attestation_data` endpoint did not wrap the response in the `data` element as expected
- Fixed an issue where a state from the previous epoch was used when calculating proposer duties
- Performed block gossip validation checks which do not require the state prior to loading it to improve performance

## 0.12.11

### Additions and Improvements
- Teku now supports running the validator client as a separate process via the `teku validator-client` subcommand. The in-process validator client is still supported. 
  
  **IMPORTANT**: To avoid being slashed, ensure validator keys are only passed as arguments to *either* the beacon node or the validator client but never both at the same time.
- Migrates to a new directory structure for data storage to provide clear separation between beacon node and validator files. Existing databases will be automatically migrated.
- Improved management of states to reduce on-heap memory usage during long periods of non-finalization.
- Head update and chain reorg events are no longer published during sync. A single coalesced event is published when sync completes.
- Reduced database growth rate during periods of non-finalization. State snapshots are only stored every two epochs instead of each epoch. This can be further adjusted with `--Xhot-state-persistence-frequency`.
- New standard REST API endpoints:
  - `/eth/v1/beacon/headers/{block_id}`
  - `/eth/v1/beacon/states/:state_id/committees/`
  - `/eth/v1/beacon/states/:state_id/committees/:epoch`
  - `/eth/v1/beacon/states/:state_id/finality_checkpoints`
  - `/eth/v1/beacon/states/:state_id/validators`
  - `/eth/v1/beacon/states/:state_id/validator_balances`
  - `/eth/v1/validator/aggregate_attestation`
  - `/eth/v1/validator/aggregate_and_proofs`
  - `/eth/v1/validator/beacon_committee_subscriptions`
  - `/eth/v1/validator/attestation_data`
  - `/eth/v1/beacon/pool/attestations`
  - `/eth/v1/beacon/states/:state_id/root`
  - `/eth/v1/beacon/blocks`
  - `/eth/v1/validator/blocks/{slot}`
- `/eth/v1/validator/duties/attester/:epoch` now includes the `committees_at_slot` value.
- `/eth/v1/validator/duties/attester/:epoch` now supports using the `POST` method to specify validator IDs, avoiding URL length limits.
- The beacon node now automatically manages persistent subnet subscriptions based on the requests to `/eth/v1/validator/beacon_committee_subscriptions` endpoint
- Log to the console if the ETH1 node's chain ID doesn't match the expected value from the ETH2 network definition. This alerts users if the ETH1 node is sync'd to the wrong ETH1 network. Thanks to `systemfreund`.
- Performance tracker reports now include information on duties which could not be performed due to an error.
- Added support for v1.0.0-rc.0 of the beacon chain spec.
- Added a `beacon_attestation_pool_size` metric to report the number of attestations held in the attestation pool.
- `StrictNoSign` requirements are now enforced on gossip messages. Messages with `from`, `signature` or `seqno` fields will be rejected.
- Removed support for `onyx` and `altona` options to the `--network` command line option. These networks used an older, incompatible version of the beacon chain spec and have been shut down.
- The `--config-file` option can now be used to provide options for all subcommands.
- Added support for checking the weak subjectivity period and checkpoint. Violations are currently reported to the log file only while the remaining details and infrastructure for handling the weak subjectivity period are finalized.


### Bug Fixes
- Fixed "Unhandled error while processes req/response" `NullPointerException`. 
- Fixed issue where performance tracker logs counted attestations twice.
- Force external signer connections to use HTTP1.1. HTTP2 connections were regularly failing in Azure.
- Fixed incompatibility with Lighthouse caused by sending an unexpected `RESET` after both sides of a stream were closed.
- Fixed issue where REST API reported a 500 internal server error when syncing instead of 503.
- Fixed issue where REST APIs returning validator information incorrectly used the effective balance instead of the actual balance.

 

## 0.12.10

### Additions and Improvements
- Added `zinken` network genesis state so an ETH1 endpoint is no longer required when connecting to Zinken.
- Deprecated the validator subcommands (which were mainly for testing) used for generating and registering validators.
- Removed the option to configure snappy compression. It is enabled by default.

### Bug Fixes
- Fixed an issue where some of the newer validator options weren't being applied in the stand alone validator client. 
- Fixed an issue where some of the blocks produced by the stand alone validator client was being dropped.
- Fixed an issue where the create block API coudn't create blocks after more than one epoch of empty slots.
- Fixed a DOS vector where queues for blocks and attestation that are not yet ready for processing could be exploited.

## 0.12.9

### Additions and Improvements
- Added `zinken` network definition. As the genesis state is not yet known, an ETH1 endpoint must be specified when connecting to the `zinken` testnet
- Added the option to output validator performance over time. Service can be enabled by using `--validators-performance-tracking-enabled`
- Implemented caching of beacon block roots to improve block import and thus sync speed
- Support symlinked keystore files
- Updated to spec version 0.12.3

### Bug Fixes
- Fixed issues where slot calculation and Store time management led to underflow errors
- Fixed issue discovered with the remote validator where the websocket publishing backed up due to slow readers

## 0.12.8

### Additions and Improvements
- Added `spadina` network genesis state so an ETH1 endpoint is no longer required when connecting to Spadina.

### Bug Fixes
- Fixed issue where topped-up up deposits did not lead to activated validators.

## 0.12.7

### Additions and Improvements
- Added `spadina` network definition. As the genesis state is not yet known, an ETH1 endpoint must be specified when connecting to the `spadina` testnet
New REST APIs
  - `/eth/v1/validator/duties/attester/:epoch` - gets attester duties for the given epoch
  - `/eth/v1/validator/duties/proposer/:epoch` - gets block proposer duties for the given epoch
  - Deprecated POST `/validator/duties`, as the new standard endpoints are now implemented
  - `eth/v1/beacon/genesis` - retrieves details of the chain's genesis
  - Deprecated the previous genesis endpoint `/node/genesis_time`
  - `/eth/v1/beacon/states/:state_id/validators/:validator_id` - gets validator from state by id
  - `/eth/v1/beacon/states/{state_id}/fork` - gets Fork object for requested state
  - Deprecated the previous fork endpoint `/node/fork`
  - `/eth/v1/events` - subscribes to beacon node events
- Implemented validator keystore file locking to prohibit another process using the same keys and getting slashed
- Updated slashing protection interchange format version to v.4
- Upgraded `Jblst` version to `0.2.0` which adds ARMv8 arch support
- Implemented sending goodbye message to peers on shutdown
- Reduced reorg noise during sync
- Updated metrics library from Besu to latest version
- Better handle P2P target peer bounds

### Bug Fixes
- Fixed debug-tools db subcommands to support writing UInt64 as YAML
- Prevented fork choice head from going backwards when updating the chain

## 0.12.6

### Additions and Improvements
- Added support for the slashing protection interchange format via the `teku slashing-protection import` and `teku slashing-protection export` subcommands
- New REST APIs
  - `/eth/v1/node/peers` - lists information about the currently connected peers
  - `/eth/v1/node/peers/:peer_id` - list information about a specific peer
  - `/eth/v1/node/health` - return the node health status via HTTP status codes. Useful for load balancers
  - `/eth/v1/node/syncing` - describe the node's current sync status
  - `/v1/node/version` has been moved to `/eth/v1/node/version` and `/v1/node/identity` to `/eth/v1/node/identity` matching changes in the standard API spec
- Gossip messages produced by Teku no longer set the `from`, `signature` or `seqNo` fields
- Enabled Gossipsub flood publishing to reduce propagation times for attestations and blocks produced locally
- Generated P2P private keys and Discovery v5 sequence numbers are now persisted across restarts
- Implemented unicode normalization process for validator keystore passwords
- Progress messages are now logged when loading large number of validator keys at startup
- The default network is now Medalla instead of Altona
- Avoid recalculating validator duties for reorgs that are not long enough to change the scheduling
- Validator duties are now calculated as soon as the genesis state is known instead of waiting for the actual genesis time.
- Added additional validation for deposit events received from the ETH1 node to flag when the ETH1 node has malfunctioned and missed some deposit log events
- Exit with a clear message when the ETH1 service is unable to start. Note that this only applies when the existing deposit data is invalid. Teku will continue retrying if the ETH1 node is not currently available.
- Operations (e.g. attestations, slashings etc) included in blocks are now readded to the pending pool if a reorg causes them to no longer be in the canonical chain
- Removed support for generating unencrypted keystores
- Discv5 now caches the hash of the local node to reduce load caused by significant numbers of incoming discovery messages
- Early access support for running the validator node independently of the beacon node (see [#2683](https://github.com/ConsenSys/teku/pull/2683) for details). Please note this is not yet a recommended configuration and the CLI options and APIs used are subject to change.

### Bug Fixes
- Gossip messages with null `from`, `signature` or `seqNo` fields are now rebroadcast with the fields still null instead of replaced by default values
- Added validation to ensure that the uncompressed length of Gossip messages is within the possible ranges for a valid message for each gossip message type
- Fixed validation of compressed gossip message length which may incorrectly reject messages
- Fixed unhandled exception reported when an attestation received over gossip had an invalid checkpoint, specifying a block root from after the specified epoch.
- Improved validation of remote peer status
- Fix "AbstractRouter internal error on message control" errors when messages are received from peers before the outbound connection has been fully established
- Fixed errors logged when the ETH1 chain is shorter than the configured follow distance
- Explicitly fsync the RocksDB write ahead log files to disk on shutdown
- Fixed issue where environment variables named `TEKU_VERSION` or `TEKU_HELP` would be incorrectly interpreted as specifying the `--version` and `--help` arguments, preventing Teku from starting
- Avoid performing duplicate tasks to regenerate states in a particular corner case when the task can be rebased to start from the output of an already scheduled task
- Improve performance of cacheable task queue used for state regeneration
- Suppressed `ClosedChannelException` errors in logs

## 0.12.5

### Bug Fixes

- Fix race condition when a block and its parents are received at around the same time which could cause the node to fall out of sync until it reverted to syncing mode to catch up
- Fix issue where attestations from blocks could be processed prior to the block they target being available resulting in `ProtoNode: Delta to be subtracted is greater than node weight` errors
- Return a non-zero exit code from `validator register` subcommand when the user does not confirm the transaction

## 0.12.4

### Additions and Improvements

- Includes a significant number of bug fixes and performance improvements as a result of the recent issues on the Medalla testnet. See https://github.com/ConsenSys/teku/issues/2596 for a full list of related issues.
- Support loading an entire directory of validator keys using `--validator-keys=<keyDir>:<passDir>`. Individual keystore and password files can also be specified using this new argument. 
- Major reduction in CPU and memory usage during periods of non-finalization by intelligently queuing and combining requests for beacon states and checkpoint states
- Fixed slow startup times during long periods of non-finalization.  Non-finalized states are now periodically persisted to disk to avoid needing to replay large numbers of blocks to regenerate state.
- Reduced sync times during long periods of non-finalization by searching for a more recent common ancestor than the finalized checkpoint 
- Added explicit UInt64 overflow and underflow protection
- Local signing is now multithreaded to better utilise CPU when running large numbers of validators
- Improved sync performance by continuing to update the target peer's status during the sync process
- Removed support for SecIO. Only the NOISE handshake is now supported.
- Provide a more userfriendly error message and exit if the P2P network port is already in use
- Added new metrics 
  - `validator_attestation_publication_delay` reports a histogram showing real time between when validations were due and when they were published to the network

### Bug Fixes

- Fixed issue where attestations were created for the wrong head because fork choice had not yet been run. Results in a significant improvement in attestation inclusion rate.
- Fixed issue where invalid blocks were produced because they included attestations from forks that had different attestation committees
- Fixed issue where block production may be scheduled for the wrong slot due to calculating duties one epoch ahead
- Fixed issue where sync could appear to stall because fork choice data wasn't being updated during sync
- Fixed race condition when updating fork choice votes which could lead to `ProtoNode: Delta to be subtracted is greater than node weight` errors
- Reduce `RejectedExecutionException` noise in logs when Teku is unable to keep up with incoming gossip messages. Other performance improvements should also improve the ability to keep up. 
- Fixed cases where states were regenerated without first checking if a cached version was available
- Fixed excessive memory usage in discovery when parsing an invalid RLP message
- Fixed issue where maximum cache sizes could be exceeded resulting in excessive memory usage
- Be more lenient in detecting peers that excessively throttle requests for blocks to better handle long stretches of empty slots
- Fixed error when validating gossiped attestations that point to old blocks
- Fixed netty thread blocked error messages from metrics by avoiding contention on key locks while retrieving metrics
- Fixed issue where sockets were left open when using an external signer


## 0.12.3

### Breaking Changes

- Removed `--validators-unencrypted-key-files` option. This was only intended for early interop testing. Keys should be loaded from encrypted keystores.

### Additions and Improvements

- Add basic built-in slashing protection. Note that this only a last line of defence against bugs in the beacon node and will not prevent slashing if validator keys are run in multiple processes simultaneously.
- Validator duty logging is now enabled by default. It can be disabled with `--log-include-validator-duties-enabled=false`
- Add updated Medalla bootnodes
- Updated to be compliant with beacon chain spec 0.12.2
- Prioritise more recent attestations when creating blocks as they pay higher rewards
- Refuse to start if the existing database is from a different network to the current configuration
- Added rate limiting for remote peers based on both number of blocks requested and total number of requests made
- Discovery now requires confirmation from multiple peers before updating the external IP reported by the node
- Improved interoperability with other clients: seqno field is now optional for libp2p messages
- REST API updates:
    - Added genesis validator root to the `/node/fork` REST API
    - Added `/validator/aggregate_attestation`
    - Added `/validator/persistent_subnets_subscription`
    - Added `/validator/beacon_committee_subscription`
    - Added `/validator/aggregate_and_proofs`
    - Added `/node/pending_attestation_count`
- Report the current peer count in "time to genesis" 
- Added UInt64 overflow and underflow detection
- Improved performance of list shuffling operations
- Snappy compression is now enabled by default for custom networks. It can be disabled with `--p2p-snappy-enabled=false`


### Bug Fixes

- Fixed vector for DOS attack caused by not throttling libp2p response rate. (See https://github.com/libp2p/jvm-libp2p/pull/127 and https://github.com/ethereum/public-attacknets/issues/7 for futher details)
- Fixed issue that delayed publication of created attestations by a slot
- Fixed "Invalid attestation: Signature is invalid" errors caused by incorrect caching of committee selections (see https://github.com/ConsenSys/teku/pull/2501 for further details)
- Fixed issues where validators failed to perform duties because the node incorrectly returned to syncing state
- Fixed `--logging` option to accept lowercase `debug` option. Renamed the `debug` subcommand to avoid the naming conflict
- Avoid lock contention when reading in-memory storage metrics
- Reduced memory usage when loading large numbers of scrypt encoded keystores
- Increased read timeout for ETH1 requests to avoid repeatedly timing out when the ETH1 node is slow
- Reduced noise in logs from `ClosedChannelException` when a peer unexpected disconnects 
- Fixed `IllegalArgumentException` when RPC response code was greater than 127
- Fixed `IllegalArgumentException` when unexpectedly short discovery messages were received
- Fixed very frequenet `InternalErrorException` when a peer disconnected during initial libp2p handshake
- Fixed crash during shutdown caused by metrics accessing RocksDB after it was closed
- Restricted the maximum epoch value accepted by REST API to ensure it can be converted to a slot without overflowing uint64
- Fixed help text for `--p2p-discovery-bootnodes`

## 0.12.2

### Additions and Improvements

- Added `medalla` network definition. As the genesis state is not yet known, an ETH1 endpoint must be specified when connecting to the `medalla` testnet
- Attestations are now created and published immediately after the block for the slot is imported, instead of waiting until 1/3rd of the way through the slot
- The Teku docker image has been upgraded to run Java 14
- `/beacon/state` REST API now supports a `stateRoot` parameter to request states by state root. This includes retrieving states for empty slots
- Reduced gas limit and used current gas price reported by the ETH1 node when sending deposit transactions with the `validator` subcommands 
- Validator keys are now loaded in parallel to improve start up time
- Added a docker-compose configuration to quickly launch a 4-node local testnet
- Exposed additional metrics to report on RocksDB memory usage:
  - storage_hot_estimated_table_readers_memory
  - storage_finalized_estimated_table_readers_memory
  - storage_hot_current_size_all_mem_tables
  - storage_finalized_current_size_all_mem_tables
- Stricter req/resp message lengths are now enforced based on message content type

### Bug Fixes

- Significant reductions in process resident memory. As this involved a configuration change for RocksDB the most significant reduction is achieved with a new database
- Fixed issue where Teku did not reconnect to peers after a network interruption
- Fixed issue where Teku may stop attempting to create new outbound peer connections
- Fixed incompatibility with deposits with public keys that could not be resolved to a G1 point
- Avoid disconnecting peers that do not return all requested blocks for a block by range request
- Reduced log level for a noisy message about duplicate peer connections

## 0.12.1

### Breaking Changes

- External signing API now uses the data field instead of signingRoot field when making signing requests. Update Eth2Signer to ensure it is compatible with this change. 

 
### Additions and Improvements

- Further reduced memory usage during periods of non-finalization. Checkpoint states can now be dropped from memory and regenerated on demand.
- Added additional metrics:
  - `beacon_peer_count` tracks the number of connected peers which have completed chain validation
  - `network_peer_chain_validation_attempts` tracks the number and status of peer chain validations
  - `network_peer_connection_attempt_count` tracks the number and status of outbound peer requests made
  - `network_peer_reputation_cache_size` reports the size of the peer reputation cache
  - `beacon_block_import_total` tracks the number of blocks imported
  - `beacon_reorgs_total` tracks the number of times a different fork is chosen as the new chain head
  - `beacon_published_attestation_total` tracks the total number of attestations sent to the gossip network
- External signing API now uses the data field instead of signingRoot field when making signing requests. Eth2Signer has been updated with this change.
- Enforced the 256 byte limit for Req/Resp error messages
- Blocks by range requests which exceed the maximum block request count are now rejected rather than partially processed as required by the P2P specification
- Improved tracking of peer reputation to avoid reattempting connections to peers we have previously rejected
- ForkChoice data is now persistent to disk, improving startup times especially during long periods of non-finalization
- Reduced the maximum number of blocks held in memory to reduce memory consumption during periods of non-finalization
- Increased the defaults for the target peer count range
- Actively manage peers to ensure we have at least some peers on each of the attestation subnets
- Maintain a minimum number of randomly selected peers, created via outbound connections to provide Sybil resistance
- Updated dependencies to latest versions


### Bug Fixes

- Fixed issue where the validator produced attestations in the incorrect slot or committee resulting in `Produce invalid attestation` messages
- Fixed an issue where attestations were not published to gossip when the node was not subscribed to the attestation subnet
- Fixed a number of unhandled exceptions in discv5
- Fixed an issue where discv5 may return node responses with a total greater than 5
- Fixed `Trying to reuse disposable LengthPrefixedPayloadDecoder` exception
- Fixed an issue where peers were not disconnected when the initial status exchange failed
- Fixed `NullPointerException` when validating attestations which became too old during validation
- Updated the `EXPOSE` ports listed in the Dockerfile to match the new defaults
- Fixed time until genesis log message to handle time zones correctly 
- Fixed `NoSuchElementException` when running a validator that was not in active status
- Fixed `IndexOutOfBoundsException` when validating an `IndexedAttestation` which included invalid validator indices

## 0.12.0

### Breaking Changes

- Upgraded to v0.12.1 of the beacon chain spec. This is compatible with the Altona and Onyx testnets.  For the Witti testnet use the 0.11.5 release.
- `--metrics-host-whitelist` CLI option has been renamed `--metrics-host-allowlist`
- `--rest-api-host-whitelist` CLI option has been renamed `--rest-api-host-allowlist`
- The Rest interface is now correctly set from `--rest-api-interface`, so will need to be correctly configured to ensure
   that hosts specified in the `--rest-api-host-allowlist` are able to access that interface. Note that the default is to listen on localhost only.
- `--rest-api-enabled` is now correctly used to determine whether to start the rest api. Ensure it is set if using the rest api. Note that the default is disabled.

### Additions and Improvements

- Support beacon chain spec v0.12.1.
- The `--network` option now includes support for `altona` and `onyx`. The default network is now `altona`
- Disk space requirements have been very significantly reduced, particularly when using archive storage mode
  - Finalized states are now stored in a separate database to non-finalized data. 
    CLI options to store these under different directories are not yet available but will be considered in the future
  - Snapshots of finalized states are stored periodically. The `data-storage-archive-frequency` option controls how frequent these snapshots are.
    More frequent snapshots result in greater disk usage but improve the performance of API requests that access finalized state.
  - Due to the way regenerated states are cached, iterating through slots in increasing order is significantly faster than iterating in decreasing order
- Teku now exposes RocksDB metrics to prometheus
- The genesis state root, block root and time are now printed at startup
- The time to genesis being reached is now output every 10 minutes, so that it's visibly apparent that teku is still running
- Requests made to ETH1 nodes now include the Teku version as the user agent
- Unstable options can now be listed with `teku -X`. These options may be changed at any time without warning and are generally not required, but can be useful in some situations.
- Added a bash/zsh autocomplete script for Teku options and subcommands
- Reduced memory usage of state caches
- The list of validators being run is now printed to the console instead of just the log file
 
 
### Bug Fixes

- Fixed a very common error message while handling received attestations from peers
- Reduced log level of `Connection reset` log messages from `NoiseXXSecureChannel`.
- Fixed an issue where the last ETH1 block with deposits was processed twice when Teku was restarted pre-genesis. This resulted in an incorrect genesis state being generated.
- Update the private key message at startup to more clearly indicate it is referring to the ENR.
- The `--rest-api-interface` configuration attribute is now set on the HTTP server, it no longer listens on all interfaces.
- The `--rest-api-enabled` flag will determine whether the http server actually starts and binds to a port now.
- Fixed minor memory leak in storage
- Fixed potential crashes in RocksDB while shutting down Teku.
- Fixed an incompatibility with other clients due to Teku incorrectly expecting a length prefix for metadata requests
- When no advertised IP is specified and the p2p-interface is set to `0.0.0.0` or some other "any local" address, 
    Teku will now resolve the IP for localhost instead of using the "any local" address in it's initial ENR.

### Known Issues

- Validator may produce attestations in the incorrect slot or committee resulting in `Produced invalid attestation` messages ([#2179](https://github.com/ConsenSys/teku/issues/2179))


## 0.11.5

### Additions and Improvements

- Reduced disk space required to store finalized data
- Improved performance when states need to be regenerated during periods of non-finalization
- Reduced on-heap memory usage
- Reduced startup time, particularly during periods of non-finalization 

## 0.11.4

### Breaking Changes

- `teku validator generate` no longer sends ETH1 deposit transactions. It only generates BLS keys for validators.
   The new `teku validator generate-and-register` subcommand can be used to generate and register validators in one step 

### Additions and Improvements

- Renamed `--metrics-host-whitelist` to `--metrics-host-allowlist` and `--rest-api-host-whitelist` to `--rest-api-host-allowlist`
- Added `/v1/node/version` and `/v1/node/identity` REST endpoints. Anyone using `/node/version` should switch to use
the new endpoint, as `/node/version` will be removed in a future release.
- Added `--validators-graffiti="GRAFFITI"` command line option to allow graffiti to be used in block production.
- The Teku version is now printed at startup
- Eth1 deposits now load on startup from the local database, then sync to the eth1 provider once loading is complete.
  A hidden flag has been added to disable this functionality if it causes any issues - `--Xeth1-deposits-from-storage-enabled=false`.
  Local storage requirements will increase slightly due to the need to store each deposit block 
  event from the eth1 provider so that it can be replayed during restarts.
- Added a `teku debug db get-deposits` subcommand to load report the ETH1 deposits from the database. This is not intended for normal use but can be useful when debugging issues.
- The directory structure created by `validator generate` has changed. All files are now generated in a single directory. 
  Filename of withdrawal key is now named using the validator public key eg 814a1a6_validator.json 814a1a6_withdrawal.json
- Validator voluntary exit gossip topics are now supported
- Proposer slashing gossip topics are now supported
- Attester slashing gossip topics are now supported
- Added support for Gossipsub 1.1
- Building from downloaded source packages rather than git checkout now works 
- Memory requirements during non-finalized periods has been reduced, but further work is required in this area
- REST API documentation is automatically published to https://pegasyseng.github.io/teku/ as soon as changes are merged 
- A memory dump is now captured if Teku encounters and out of memory error. By default these are
   written to the current work directory but an alternate location can be specified by setting the
   environment variable `TEKU_OPTS=-XX:HeapDumpPath=/path`
   This can be disabled by setting `TEKU_OPTS=-XX:-HeapDumpOnOutOfMemoryError` 
- Treat attestations from verified blocks or produced in the validator as "seen" for gossip handling
- Added metrics to report the active and live validators for the current and previous epochs
   (`beacon_previous_live_validators`, `beacon_previous_active_validators`, `beacon_current_live_validators`, `beacon_current_active_validators`)
- Added metrics to report on the in-memory store. Includes state cache hit/miss rates and the current number of states, blocks and checkpoint states held in the store.
- Optimised decompression of points in BLS
- Keystores generated with Teku are now compatible with Lighthouse
- Added additional logging during start up to provide progress information while hot states are being regenerated
- Integrated the new fork choice reference tests


### Bug Fixes

- Fixed `StackOverflowException` from fork choice `get_ancestor` method during long periods of non-finalization
- Fixed issue where ETH1 events could be missed immediately after startup resulting in an incorrect genesis state being generated
- Fixed issue where the beacon node was considered in-sync prior to finding any peers
- Recalculate validator duties when a block is imported which may affect duty scheudling.
   This occurs when blocks are delayed for more than epoch, for example due to a network outage
- Block production no longer fails if the Eth1Data vote in the new block is the last required vote 
  for that Eth1Data and the new Eth1Data allows inclusion of new deposits
- `io.netty.handler.timeout.ReadTimeoutException` and `io.netty.channel.ExtendedClosedChannelException` are no longer reported at `ERROR` level
- Fix `attestation is invalid` error messages caused by a race condition verifying signatures
- Support zero-roundtrip multistream negotiations in libp2p
- Fix missed assertion in fork choice which could lead to an `IndexOutOfBoundException` (not consensus affecting)
- Clarified the "Minimum genesis time reached" message to be clearer that this only indicates that an
   ETH1 block satisfying the time criteria for genesis has been found. 
   Additional validators may still be required before the genesis state is known.
- Fixed a number of error messages that were logged during Teku shutdown
- Respect the optional `epoch` paramter to the `/beacon/validators` REST API endpoint
- Fix "Command too long" error when running on Windows
- Fixed issue where exceptions may be reported by the uncaught exception handler instead of reported back to the original caller.
   This resulted in some RPC streams not being closed correctly.
- Fixed execessive use of CPU regenerating states to map slots to block roots while servicing beacon block by root RPC requests.


### Known Issues


## 0.11.3

### Breaking Changes

- The `--eth1-enabled` option removed. ETH1 will be enabled when an `--eth1-endpoint` is provided and otherwise disabled. 
- CLI option `--validators-key-file` renamed to `--validators-unencrypted-key-file` to avoid ambiguity from similar 
named CLI option `--validators-key-files` which is used to specify encrypted validator keystore files.
- Added CLI option `--rest-api-host-whitelist` which restricts access to the REST API. Defaults to [localhost, 127.0.0.1]
- External signer API has been changed to not include the type being signed. Please ensure you update to the latest version of Eth2Signer
- `peer generate` and `genesis mock` subcommands now use lower case options consistently: 
`--outputFile` is renamed `--output-file`, `--validatorCount` is renamed `--validator-count`, and `--genesisTime` is renamed `--genesis-time`

### Additions and Improvements

- `--network witti` includes the final configuration for the Witti testnet. The genesis state is included so an ETH1 endpoint is no longer required when connecting to Witti
- Teku can now use Infura as the ETH1 endpoint
- ETH1 node is no longer required to maintain historic world state
- Added `--log-include-validator-duties-enabled` option to enable log messages when validator clients produce blocks, attestations or aggregates (defaults to off)
- Improved logging of errors during execution of validator duties to be more informative, less noisy and set log levels more appropriately
- Teku will now exit when an `OutOfMemoryError` is encountered to allow tools like systemd to restart it
- Added support for compiling from source using Java 14
- Improved error messages for a number of configuration errors
- Added protection for DNS rebinding attacks to the REST API via host whitelisting. Configure available hosts with the `--rest-api-host-whitelist` option (defaults to `[localhost, 127.0.0.1]`)
- Simplified API for external signers
- Added support for the `name` field in keystore files 
- Improved reporting of errors when using `BOTH` or default log destinations. Unhandled exceptions are now reported to the console but without stack traces. The full stack trace is available in the log file
- Report (to log file) the list of validators being run at startup.
- Append to existing log files rather than rolling them. Avoids the potential for logs to be lost when rolling

### Bug Fixes

- Improved selection of attestations to include in proposed blocks. 
  - Include attestations received via gossip
  - Exclude attestations that have already been included in blocks
  - Fix issue where attestations with an incompatible source were included, resulting in an invalid block
- Fixed a file descriptor leak caused by not correctly disconnecting duplicate peer connections 
- Fixed race condition when the genesis event occurs which prevented the validator client from subscribing to persistent committee topics and retrieving the initial duties
- ETH1 chain processing recovers better after interruptions to the ETH1 node
- RPC `STATUS` messages now use the finalized checkpoint from the state, not the fork choice store. Fixes a networking incompatibility with Lighthouse when only genesis has been finalized
- Fix incompatibility with Lighthouse in how RPC `METADATA` requests are made
- Large RPC response chunks (> 90K) are now correctly processed
- Improved validation of received attestations
- Fixed a number of race conditions which could lead to inconsistent data being reported via the REST API
- Stopped logging the Javalin ascii art banner during startup
- The `peer generate` subcommand now provides a useful error message when the output file can not be written 
- The `peer generate` subcommand no longer silently overwrites an existing output file
- Interop validators are no longer loaded when no validator keys were specified
- Fixed or suppressed a number of `ERROR` level log messages
- Non-fatal errors are no longer reported at `FATAL` log level

### Known Issues

- Block production may fail if the Eth1Data vote in the new block is the last required vote for that Eth1Data and the new Eth1Data allows inclusion of new deposits
- `io.netty.handler.timeout.ReadTimeoutException` and `io.netty.channel.ExtendedClosedChannelException` reported at `ERROR` level.


## 0.11.2

### Additions and Improvements

- Updated to spec version v0.11.3.
- Improved recovery from network changes. Peers are now disconnected if they do not respond for a 
  period ensuring upstream network interruptions are detected and peers can reconnect.
- The node's ENR is printed at startup even if the genesis state is not yet known.
  As per the beacon chain spec, the network ports are still not opened until the genesis state is known.
- OpenAPI schemas are now more compatible with code generating tools.
- Include block root in `/beacon/block` responses.
- Improved error messages when invalid or incompatible CLI options are provided.
- Improved peer discovery by filtering out peers with incompatible `eth2` ENR fields.
- Improved performance of BLS signature verification
- Updated to jvm-libp2p 0.4.0

### Bug Fixes

- Fixed a deadlock condition which could cause block imports to silently stall.
- Initial sync now reaches chain head correctly even when the chain has not finalized for more than 10 epochs.
- Fixed `NullPointerException` and `ArrayIndexOutOfBoundException` intermittently encountered when importing blocks 
  due to a concurrency issue in batch signature verification.
- `/beacon/chainhead` reported incorrect slot and block root data.
- Fixed a range of race conditions when loading chain data which could result in inconsistent views 
  of the data or data not being found as it moved from recent to finalized storage.
- Significantly reduced the number of ERROR level log messages. 
  Invalid network data or unexpectedly disconnected peers is now logged at DEBUG level.
- Storage system did not correctly prune blocks loaded from disk on startup when they became finalized.


### Known Issues

- This release provides support for the Witti testnet via `--network witti` however the configuration 
  for this testnet is not yet stable and will likely differ from the one currently used.
- The Schlesi testnet has been abandoned. The `--network schlesi` option will be removed in a future release.
- Memory usage grows signficantly during periods of non-finalization.
- Teku requires the ETH1 endpoint to keep historic world state available for at least the ETH1 voting period. 
  This is typically more historic state than is kept when ETH1 nodes are pruning state. 
  Workaround is to connect to an archive node or configure the node to preserve a greater period of historic world state.  
