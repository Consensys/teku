#!/bin/sh

rm -rf demo
mkdir -p demo

tar -zxf ../build/distributions/artemis-*.tar.gz -C ./demo/
mv ./demo/artemis-* ./demo/node_0
tar -zxf ../build/distributions/artemis-*.tar.gz -C ./demo/
mv ./demo/artemis-* ./demo/node_1
tar -zxf ../build/distributions/artemis-*.tar.gz -C ./demo/
mv ./demo/artemis-* ./demo/node_2
tar -zxf ../build/distributions/artemis-*.tar.gz -C ./demo/
mv ./demo/artemis-* ./demo/node_3

ln -s ../../../config ./demo/node_0/
ln -s ../../../config ./demo/node_1/
ln -s ../../../config ./demo/node_2/
ln -s ../../../config ./demo/node_3/

cd demo/node_0 && ln -s ./bin/artemis . && cd ../../
cd demo/node_1 && ln -s ./bin/artemis . && cd ../../
cd demo/node_2 && ln -s ./bin/artemis . && cd ../../
cd demo/node_3 && ln -s ./bin/artemis . && cd ../

tmux new-session -d -s foo 'cd node_0 && ./artemis --config=./config/demoConfig.0.toml'
tmux split-window -v -t 0 'cd node_1 && ./artemis --config=./config/demoConfig.1.toml'
tmux split-window -h 'cd node_2 && ./artemis --config=./config/demoConfig.2.toml'
tmux split-window -v -t 1 'cd node_3 && ./artemis --config=./config/demoConfig.3.toml'
tmux select-layout tile
tmux rename-window 'the dude abides'
tmux attach-session -d
