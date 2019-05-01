#!/bin/sh

source $(dirname $0)/run_functions.sh

clean demo

NODES=$1
i=0

while [ $i -lt $NODES ] 
do
  configure_node $i
  i=$(($i + 1))
done

cd demo/

tmux new-session -d -s foo 'cd node_0 && ./artemis --config=./config/demoConfig.0.toml --logging=INFO'
tmux split-window -v -t 0 'cd node_1 && ./artemis --config=./config/demoConfig.1.toml --logging=INFO'
tmux split-window -h 'cd node_2 && ./artemis --config=./config/demoConfig.2.toml --logging=INFO'
tmux split-window -v -t 1 'cd node_3 && ./artemis --config=./config/demoConfig.3.toml --logging=INFO'
tmux select-layout tile
tmux rename-window 'the dude abides'
tmux attach-session -d
