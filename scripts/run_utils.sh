#!/bin/sh

# Create the configuration file for a specific node
create_config() {
  local NODE="$1"; local TOTAL="$2"; local TEMPLATE="$3"

  # Create the peer list by removing this node's peer url from the peer list 
  local PEERS=$(echo "$PEERS" | sed "s/\"hob+tcp:\/\/abcf@localhost:$((19000 + $NODE))\"//g")
  PEERS="[$(echo $PEERS | tr ' ' ',')]"

  # Set the port number to the node number plus 19000
  local PORT=$((19000 + $NODE))

  # Set IDENTITY to the one byte hexadecimal representation of the node number
  local IDENTITY; setAsHex $NODE "IDENTITY"

  # Create the configuration file for the node
  cat $TEMPLATE | \
    sed "s/logFile\ =.*/logFile = \"artemis-$NODE.log\"/"             |# Use a unique log file
    sed "s/advertisedPort\ =.*//"                                     |# Remove the advertised port field
    sed "s/identity\ =.*/identity\ =\ \"$IDENTITY\"/"                 |# Update the identity field to the value set above
    sed "s/port\ =.*/port\ =\ $PORT/"                                 |# Update the port field to the value set above
    awk -v peers="$PEERS" '/port/{print;print "peers = "peers;next}1' |# Update the peer list 
    sed "s/numNodes\ =.*/numNodes\ =\ $TOTAL/"                        |# Update the number of nodes to the total number of nodes
    sed "s/networkInterface\ =.*/networkInterface\ =\ \"127.0.0.1\"/" |# Update the network interface to localhost
    sed "s/networkMode\ =.*/networkMode\ =\ \"hobbits\"/" \
    > ../config/runConfig.$NODE.toml
}

# Unpacks the build tar files, puts them in a special directory for the node,
# and creates the configuration file for the node.
configure_node() {
  local NODE=$1

  # Unpack the build tar files and move them to the appropriate directory
  # for the node.
  tar -zxf ../build/distributions/artemis-*.tar.gz -C ./demo/
  mv ./demo/artemis-* ./demo/node_$NODE

  # Create symbolic links for the demo
  ln -s ../../../config ./demo/node_$NODE/
  cd demo/node_$NODE && ln -s ./bin/artemis . && cd ../../

  # Create the configuration file for the node
  if [ "$3" == "" ] 
  then 
    create_config $NODE $2 "../config/config.toml"
  else
    create_config $NODE $2 $3
  fi
}

# Create tmux panes in the current window for the next "node group".
# A node group is a group of up to 4 nodes that are all started on the same window. 
# These groups are used to ensure that tmux will be able to create all of 
# windows without running out of screen space
create_tmux_panes() {
  idx=$1

  # Set variables to stop the loop
  local end=$(($idx + 3))

  # Create at most 4 vertical splits for the next nodes in the group
  while [[ $idx -lt $NODES && $idx -lt $end ]]
  do
    # Split the window vertically and start the next node in the new vertical split
    tmux split-window -v "cd node_$idx && ./artemis --config=./config/runConfig.$idx.toml --logging=INFO"
    idx=$(($idx + 1))
  done
}

# Create tmux windows for every 9 nodes, and create a last window for any remaining nodes.
# Due to the constraints of tmux, only 9 panes could be created in a single window.
create_tmux_windows() {
  local NODES=$1
  local VERTICAL=$2

  cd demo/

  # Create a new tmux session and start it with the first artemis node
  tmux new-session -d -s foo 'cd node_0 && ./artemis --config=./config/runConfig.0.toml --logging=INFO'

  # Start the index at 1 because the first node has already been created
  idx=1
  # Create new tmux panes for the first 4 nodes
  create_tmux_panes $idx

  ## Use the vertical layout if the flag is set
  if [ $VERTICAL == true ]
  then
    tmux select-layout even-vertical
  else
    # Use the tiled layout for the window to make the panes as close to equally sized as possible
    tmux select-layout tiled
  fi

  # Rename the window to add some spice
  tmux rename-window 'wwjdd'

  # Loop over the remaining nodes
  while [[ $idx -lt $NODES ]]
  do
    # Start a new tmux window with the next node. Give it a name to add some more spice
    tmux new-window -n 'the dude abides again...' "cd node_$idx && ./artemis --config=./config/runConfig.$idx.toml --logging=INFO"
    idx=$(($idx + 1))
    # Create new tmux panes for the new 4 nodes, or as many as possible if there are less than 4
    create_tmux_panes $idx
    # Use the tiled layout for the window to make the panes as close to equally sized as possible
    tmux select-layout tiled
  done
  
  # Attach the session to start the simulation
  tmux attach-session -d
}

generate_peers_list() {
  seq $1 $(($1 + $2 - 1)) | sed -E "s/([0-9]+)/\"$3:\/\/$4:\1\"/g"
}

# Takes a number, $1, and the name of an environment variable, $2, and sets the environment variable in the parent shell 
# with the specified name to the hexadecimal representation of the provided number.
setAsHex() {
  if [[ $1 -lt 16 ]]
  then
    printf -v "$2" "0x0%X" $1
  else
    printf -v "$2" "0x%X" $1
  fi
}
