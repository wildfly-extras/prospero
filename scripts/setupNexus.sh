#!/bin/bash

NEXUS_HOST=${NEXUS_HOST:-"http://localhost:8081"}
NEXUS_API_PATH=${NEXUS_API_PATH:-"/service/rest/v1"}
NEXUS_CONTAINER=${NEXUS_CONTAINER:-"nexus"}
NEXUS_ADMIN_PASSWORD=${NEXUS_ADMIN_PASSWORD:-"admin"}

SCRIPTPATH=$(realpath $(dirname ${0}))

# start docker instance if not started

if [ ! "$(which docker)" ];
then
  echo "Docker binary not found"
  exit 1
fi

docker info > /dev/null 2>&1
if [ $? -ne 0 ]; then
  echo "Unable to connect to docker - is it running?"
  exit 2
fi

RUN_SETUP="false"
RUNNING=$(docker inspect --format="{{.State.Running}}" $NEXUS_CONTAINER 2> /dev/null)

if [ "$?" -eq 1 ];
then
  # create new 
  echo "Starting new ${NEXUS_CONTAINER}"
  docker run -d -p 8081:8081 --name nexus sonatype/nexus3 > /dev/null
  RUN_SETUP="true"
  RUNNING="false"
elif [ "${RUNNING}" == "false" ];
then
  #restart
  echo "Restarting ${NEXUS_CONTAINER}"
  docker start $NEXUS_CONTAINER > /dev/null
else
  echo "${NEXUS_CONTAINER} already running"
fi

# wait for container to start
while [ "${RUNNING}" == "false" ]
do
  echo "..."
  sleep 5
  curl "${NEXUS_HOST}${NEXUS_API_PATH}/status" 2> /dev/null
  if [ "$?" == "0" ];
  then
    RUNNING="true"
    echo "${NEXUS_CONTAINER} started"
    break
  fi
done

if [ "${RUN_SETUP}" == true ];
then
  TMP_PASSWORD=$(docker exec ${NEXUS_CONTAINER} cat /nexus-data/admin.password)

  # set admin password
  curl -H "Content-Type: text/plain" -X PUT -u "admin:${TMP_PASSWORD}" -d "${NEXUS_ADMIN_PASSWORD}" "${NEXUS_HOST}${NEXUS_API_PATH}/security/users/admin/change-password" 2> /dev/null
  if  [ "$?" != 0 ];
  then
    echo "Unable to set admin password"
    exit 3
  fi


  # create repos
  curl -H "Content-Type: application/json" -u "admin:${NEXUS_ADMIN_PASSWORD}" -X POST --data @"${SCRIPTPATH}/dev-repository.json" "${NEXUS_HOST}${NEXUS_API_PATH}/repositories/maven/hosted" 2> /dev/null
  curl -H "Content-Type: application/json" -u "admin:${NEXUS_ADMIN_PASSWORD}" -X POST --data @"${SCRIPTPATH}/galleon-repository.json" "${NEXUS_HOST}${NEXUS_API_PATH}/repositories/maven/hosted" 2> /dev/null
fi
