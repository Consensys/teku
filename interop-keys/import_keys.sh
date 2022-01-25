#!/usr/bin/env bash

 # Copyright 2021 ConsenSys AG.
 #
 # Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 # the License. You may obtain a copy of the License at
 #
 # http://www.apache.org/licenses/LICENSE-2.0
 #
 # Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 # an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 # specific language governing permissions and limitations under the License.

for COMMAND in "jq" "curl" "echo" "cat"
do
  command -v $COMMAND 2>/dev/null || { echo >&2 "I require $COMMAND but it's not installed.  Aborting."; exit 1; }
done

SIGNER_URL="http://localhost:19000"
echo "Initialising payload.json..."
echo "{ \"keystores\": [" > payload.json

echo "  - > writing keystores..."
for FILE in `ls keys/*.json`
do
  if [ ! -z "$D" ]
  then
    echo "," >> payload.json
  else
    D=1
  fi
  cat $FILE|jq -c |jq -Rs . >> payload.json
done
echo "],
\"passwords\": [" >> payload.json
echo "  - > writing passwords..."
for FILE in `ls passwords/*.txt`
do
  if [ ! -z "$P" ]
  then
    echo "," >> payload.json
  else
    P=1
  fi
  cat $FILE|jq -Rs .>> payload.json
done
echo "]
}" >> payload.json

echo "Sending payload.json to ${SIGNER_URL}/eth/v1/keystores..."
curl -q -X POST ${SIGNER_URL}/eth/v1/keystores \
 -H "Content-Type: application/json" \
 -d @payload.json \
 -o result.json
 echo "Wrote result to result.json."
