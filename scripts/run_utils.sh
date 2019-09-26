#!/bin/sh

# Create the configuration file for a specific node
create_config() {
  local MODE="$1"; local NODE="$2"; local TOTAL="$3"; local TEMPLATE="$4"



  # Set the port number to the node number plus 19000
  local PORT=$((19000 + $NODE))


  local IS_BOOTNODE=false

  # Set IDENTITY to the one byte hexadecimal representation of the node number
  local IDENTITY; setAsHex $NODE "IDENTITY"

  # this can be reactivated once we have a java discv5
  #local BOOTNODES=$(cat ~/.mothra/network/enr.dat)

  if [ "$NODE" == "0" ]
  then
    # Create a list of peer ids
    cd demo/node_0 && ./artemis peer generate $NUM -o "config/peer_ids.dat" && cd ../../
  fi
  # Create a list of all the peers for the configure node procedure to use
  PEERS=$(generate_peers 19000 $NUM $NODE)
  PEERS=$(echo $PEERS | tr -d '\n')
  PEERS="[$(echo $PEERS | tr ' ' ',')]"

  # get the private key for this node
  local PRIVATE_KEY=$(sed "$(($NODE + 2))q;d" ../config/peer_ids.dat | cut -f 1)

  # Create the configuration file for the node
  cat $TEMPLATE | \
    sed "s/logFile\ =.*/logFile = \"artemis-$NODE.log\"/"             |# Use a unique log file
    sed "s/advertisedPort\ =.*//"                                     |# Remove the advertised port field
    sed "s/identity\ =.*/identity\ =\ \"$IDENTITY\"/"                 |# Update the identity field to the value set above
    sed "s/isBootnode\ =.*/isBootnode\ =\ $IS_BOOTNODE/"              |# Update the bootnode flag
    sed "s/bootnodes\ =.*/bootnodes\ =\ \"$BOOTNODES\"/"              |# Update the bootnodes
    sed "s/privateKey\ =.*/privateKey\ =\ \"$PRIVATE_KEY\"/"          |# Update the private key
    sed "s/port\ =.*/port\ =\ $PORT/"                                 |# Update the port field to the value set above
    sed "s/genesisTime\ =.*/genesisTime\ =\ $GENESIS_TIME/"           |# Update the genesis time
    awk -v peers="$PEERS" '/port/{print;print "peers = "peers;next}1' |# Update the peer list
    sed "s/numNodes\ =.*/numNodes\ =\ $TOTAL/"                        |# Update the number of nodes to the total number of nodes
    sed "s/networkInterface\ =.*/networkInterface\ =\ \"127.0.0.1\"/" |# Update the network interface to localhost
    sed "s/networkMode\ =.*/networkMode\ =\ \"$MODE\"/" \
    > ../config/runConfig.$NODE.toml

}



# Unpacks the build tar files, puts them in a special directory for the node,
# and creates the configuration file for the node.
configure_node() {
  local MODE=$1
  local NODE=$2
  local NUM=$3

  # Unpack the build tar files and move them to the appropriate directory
  # for the node.
  tar -zxf ../build/distributions/artemis-*.tar.gz -C ./demo/
  mv ./demo/artemis-* ./demo/node_$NODE

  # Create symbolic links for the demo
  ln -sf ../../../config ./demo/node_$NODE/
  cd demo/node_$NODE && ln -sf ./bin/artemis . && cd ../../



  # Create the configuration file for the node
  if [ "$4" == "" ]
  then 
    create_config $MODE $NODE $NUM "../config/config.toml"
  else
    create_config $MODE $NODE $NUM $4
  fi


  # in
  rm -rf demo/node_$NODE/*.json
  cp ../*.json demo/node_$NODE/
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
  tmux new-session -d -s foo "cd node_0 && ./artemis --config=./config/runConfig.0.toml --logging=$LOG"

  # Start the index at 1 because the first node has already been created
  idx=1
  # Create new tmux panes for the first 4 nodes
  create_tmux_panes $idx

  ## Use the vertical layout if the flag is set
  if [ "$VERTICAL" == true ]
  then
    tmux select-layout even-vertical
  else
    # Use the tiled layout for the window to make the panes as close to equally sized as possible
    tmux select-layout tiled
  fi

  # Rename the window to add some spice
  tmux rename-window 'the dude abides...'

  # Loop over the remaining nodes
  while [[ $idx -lt $NODES ]]
  do
    # Start a new tmux window with the next node. Give it a name to add some more spice
    tmux new-window -n 'the dude abides again...' "cd node_$idx && ./artemis --config=./config/runConfig.$idx.toml --logging=$LOG"
    idx=$(($idx + 1))
    # Create new tmux panes for the new 4 nodes, or as many as possible if there are less than 4
    create_tmux_panes $idx
    # Use the tiled layout for the window to make the panes as close to equally sized as possible
    tmux select-layout tiled
  done
  
  # Attach the session to start the simulation
  tmux attach-session -d
}

generate_peers() {
  local STARTING_PORT=$1
  local NODES=$2
  local NODE=$3
  local PEERS=$(seq $STARTING_PORT $(($STARTING_PORT + $NODES - 1)) | sed -E "s/([0-9]+)/\"\/ip4\/127\.0\.0\.1\/tcp\/\1\/p2p\//g")
  local PEER_ARRAY=($PEERS)
  local RESULT

  for i in "${!PEER_ARRAY[@]}"
  do
    if [ "$NODE" != "$i" ]
    then
      RESULT[$i]=${PEER_ARRAY[$i]}$(sed "$(($i + 2))q;d" ../config/peer_ids.dat | cut -f 3)
      RESULT[$i]+="\""
    fi
  done

  echo "${RESULT[@]}"
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
