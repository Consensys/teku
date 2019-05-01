#!/bin/sh

DIR=$(dirname $0)
NODES=$1

source $DIR/utils.sh

if [[ "$#" -ne 0 || "$NODES" -lt 1 ]]
then 
  usage 
  exit
fi

#configure_node 0 5
COMBINATIONS=$(seq 19000 $((19000 + $NODES - 1)))
PEERS=$(echo "$COMBINATIONS" | sed -E "s/^([0-9]+)/\"hob+tcp:\/\/abcf@localhost:\1\"/g")

# Clean the demo directory
clean demo

# Loop over all of the nodes to be created and configure them
i=0
while [ $i -lt $NODES ] 
do
  configure_node $i $NODES
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
