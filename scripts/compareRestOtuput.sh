#!/bin/sh

state_id=finalized
block_id=2481823
epoch=77557
slot=2481823
validator_id=0
declare -a endpoints=("/eth/v1/beacon/genesis"
"/eth/v1/beacon/states/${state_id}/root"
"/eth/v1/beacon/states/${state_id}/fork"
"/eth/v1/beacon/states/${state_id}/finality_checkpoints"
"/eth/v1/beacon/states/${state_id}/validators"
"/eth/v1/beacon/states/${state_id}/validators/${validator_id}"
"/eth/v1/beacon/states/${state_id}/validator_balances"
"/eth/v1/beacon/states/${state_id}/committees"
"/eth/v1/beacon/states/${state_id}/sync_committees"
"/eth/v1/beacon/headers"
"/eth/v1/beacon/headers/${block_id}"
"/eth/v1/beacon/blocks/${block_id}/root"
"/eth/v1/beacon/blocks/${block_id}/attestations"
"/eth/v1/beacon/pool/attestations"
"/eth/v1/beacon/pool/attester_slashings"
"/eth/v1/beacon/pool/proposer_slashings"
"/eth/v1/beacon/pool/voluntary_exits"
"/eth/v2/beacon/blocks/${block_id}"
"/eth/v1/config/deposit_contract"
"/eth/v1/config/fork_schedule"
"/eth/v1/config/spec"
"/eth/v1/debug/beacon/heads"
"/eth/v1/debug/beacon/states/${state_id}"
"/eth/v2/debug/beacon/states/${state_id}"
"/eth/v1/events"
"/eth/v1/node/health"
"/eth/v1/node/identity"
"/eth/v1/node/peer_count"
"/eth/v1/node/peers"
"/eth/v1/node/syncing"
"/eth/v1/node/version"
"/eth/v1/validator/aggregate_attestation"
"/eth/v1/validator/attestation_data"
"/eth/v1/validator/blocks/${slot}"
"/eth/v2/validator/blocks/${slot}"
"/eth/v1/validator/duties/proposer/${epoch}"
"/eth/v1/validator/sync_committee_contribution"
"/teku/v1/admin/liveness"
"/teku/v1/admin/readiness"
"/teku/v1/beacon/blocks/${block_id}/state"
"/teku/v1/beacon/blocks/${slot}"
"/teku/v1/beacon/states/${state_id}"
"/teku/v1/nodes/peer_scores"
)

hostA="http://localhost:5051"
hostB="http://localhost:6051"

for i in "${endpoints[@]}"
do
  urlA="${hostA}$i"
  urlB="${hostB}$i"
  curlAoutput=$(curl -s ${urlA})
  curlBoutput=$(curl -s ${urlB})
  if [[ "$curlAoutput" == "$curlBoutput" ]]
  then
    echo "The following endpoint match"
    echo $urlA
  else
    echo "The following endpoint dont match the return value"
    echo $urlA
  fi
done

#"/eth/v1/node/peers/{peer_id}"
#PUT "/teku/v1/admin/log_level"
#POST "/eth/v1/beacon/blocks"
#POST "/eth/v1/beacon/pool/sync_committees"
#POST "/eth/v1/validator/aggregate_and_proofs"
#POST "/eth/v1/validator/beacon_committee_subscriptions"
#POST "/eth/v1/validator/contribution_and_proofs"
#POST "/eth/v1/validator/duties/attester/{epoch}"
#POST "/eth/v1/validator/duties/sync/{epoch}"
#POST "/eth/v1/validator/liveness"
#POST "/eth/v1/validator/sync_committee_subscriptions"
