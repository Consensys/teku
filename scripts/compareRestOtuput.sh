#!/bin/sh

state_id=finalized
block_id=2481823
epoch=77557
slot=2481823
declare -a endpoints=("/eth/v1/beacon/genesis"
"/eth/v1/beacon/states/${state_id}/root"
"/eth/v1/beacon/states/${state_id}/fork"
"/eth/v1/beacon/states/${state_id}/finality_checkpoints"
"/eth/v1/beacon/states/${state_id}/validators"
#"/eth/v1/beacon/states/${state_id}/validators/{validator_id}"
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
