# Changelog

Due to the rapidly changing nature of ETH2 testnets and rapid rate of improvements to Teku, 
we recommend most users use the latest `master` branch of Teku.

## Upcoming Breaking Changes

- Anyone using `/node/version` should switch to use
   the new `/v1/node/version` endpoint, as `/node/version` will be removed in a future release.

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

- Validator may produce attestations in the incorrect slot or committee resulting in `Produced invalid attestation` messages ([#2179](https://github.com/PegaSysEng/teku/issues/2179))


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
