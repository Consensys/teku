#!/bin/bash

git clone git@github.com:jrhea/eth2.0-spec-tests-template.git /tmp/eth2.0-spec-tests-template
git clone --recursive --template=/tmp/eth2.0-spec-tests-template git@github.com:PegaSysEng/artemis.git
