# Changelog

## Upcoming Breaking Changes
 
## Current Releases

## Unreleased Changes

### Breaking Changes
- `GOSSIP_MAX_SIZE`, `MAX_CHUNK_SIZE`, `TTFB_TIMEOUT` and `RESP_TIMEOUT` configuration variables are no longer exported as they were removed from spec. 
  Any release compliant with fulu (fusaka) will not require these to be present, but earlier releases may no longer be able to consume this configuration.

### Additions and Improvements

- Add User-Agent header to requests initiated from the Validator Client with the client identifier and version.
- Increase Default validator registration Gas Limit 60M for all networks.
- Rename Fulu metric for data_column_sidecar_by_root RPC request to `network_rpc_data_column_sidecars_by_root_requested_sidecars_total`.  

### Bug Fixes
