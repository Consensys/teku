#!/bin/sh

rm -rf demo
mkdir -p demo

tar -zxf ../build/distributions/artemis-*.tar.gz -C ./demo/
mv ./demo/artemis-* ./demo/node_0
tar -zxf ../build/distributions/artemis-*.tar.gz -C ./demo/
mv ./demo/artemis-* ./demo/node_1
tar -zxf ../build/distributions/artemis-*.tar.gz -C ./demo/
mv ./demo/artemis-* ./demo/node_2

ln -s ../../../config ./demo/node_0/
ln -s ../../../config ./demo/node_1/
ln -s ../../../config ./demo/node_2/

cd demo/node_0 && ln -s ./bin/artemis . && cd ../../
cd demo/node_1 && ln -s ./bin/artemis . && cd ../../
cd demo/node_2 && ln -s ./bin/artemis . && cd ../

tmux new-session -d -s foo 'cd node_0 && ./artemis --config=./config/testConfig.0.toml --logging=INFO'
tmux split-window -v -t 0 'cd node_1 && ./artemis --config=./config/testConfig.1.toml --logging=INFO'
tmux split-window -h 'cd node_2 && ./artemis --config=./config/testConfig.2.toml --logging=INFO'
tmux select-layout tile
tmux rename-window 'the dude abides'
tmux attach-session -d
