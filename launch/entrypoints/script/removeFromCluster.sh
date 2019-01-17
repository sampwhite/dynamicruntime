#!/bin/sh
curl -s -X PUT -o /dev/null http://localhost:7070/node/setClusterMembership?isClusterMember=0
# Allow time for load balancer to pick this up.
echo "Waiting 15 seconds for node to detach from cluster"
sleep 15