#!/bin/bash

readonly REPO_ID=${REPO_ID:-"nexus2"}
readonly REPO_URL=${REPO_URL:-"http://localhost:8081/repository/galleon"}

readonly WILDFLY_CORE_FP="wildfly-core-galleon-pack"
readonly WILDFLY_FPS=("wildfly-galleon-pack" "wildfly-ee-galleon-pack")
readonly GALLEON_PLUGINS_JAR="wildfly-galleon-plugins"

deploy () {
    if [[ "${1}" == *"sources"* ]];
    then
        return
    fi

    file="${1}"
    group_id="${2}"
    artifact_id="${3}"
    packaging="${4}"
    filename="$(basename ${file})"

    version="${filename/${artifact_id}-/}"
    version="${version/.${packaging}/}"
    echo "Deploying ${group_id} ${artifact_id} ${version} ${packaging}"

   mvn deploy:deploy-file -Dfile="${file}" -DrepositoryId="${REPO_ID}" \
    -Durl="${REPO_URL}" -DartifactId="${artifact_id}" \
    -DgroupId="${group_id}" -Dversion="${version}" \
    -Dpackaging="${packaging}"
}

if [ -z "${WILDFLY_SRC}" ] || [ -z "${WILDFLY_CORE_SRC}" ] || [ -z "${GALLEON_PLUGIN_SRC}" ];
then
  echo "WILDFLY_SRC, WILDFLY_CORE_SRC and GALLEON_PLUGIN_SRC env variables need to be set"
  exit 1
fi

find "${WILDFLY_CORE_SRC}" -name "${WILDFLY_CORE_FP}-*.zip" -print0 | while read -d $'\0' file
do
    deploy "${file}" "org.wildfly.core" "${WILDFLY_CORE_FP}" "zip"
done

for fp in "${WILDFLY_FPS[@]}"
do
    find "${WILDFLY_SRC}" -name "${fp}-*.zip" -print0 | while read -d $'\0' file
    do
        deploy "${file}" "org.wildfly" "${fp}" "zip"
    done
done

find "${GALLEON_PLUGIN_SRC}" -name "${GALLEON_PLUGINS_JAR}-*.jar" -print0 | while read -d $'\0' file
do
    deploy "${file}" "org.wildfly.galleon-plugins" "${GALLEON_PLUGINS_JAR}" "jar"
done
