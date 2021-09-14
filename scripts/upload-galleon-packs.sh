#!/bin/bash

readonly REPO_ID=${REPO_ID:-"nexus2"}
readonly REPO_URL=${REPO_URL:-"http://localhost:8081/repository/galleon"}
readonly LOCAL_MVN_REPO=${LOCAL_MVN_REPO:-"${HOME}/.m2/repository"}

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


#if [ -z "${WILDFLY_SRC}" ] || [ -z "${WILDFLY_CORE_SRC}" ] || [ -z "${GALLEON_PLUGIN_SRC}" ];
#then
#  echo "WILDFLY_SRC, WILDFLY_CORE_SRC and GALLEON_PLUGIN_SRC env variables need to be set"
#  exit 1
#fi

if [ -n "${WILDFLY_CORE_SRC}" ];
then
  find "${WILDFLY_CORE_SRC}" -name "${WILDFLY_CORE_FP}-*.zip" -print0 | while read -d $'\0' file
  do
     deploy "${file}" "org.wildfly.core" "${WILDFLY_CORE_FP}" "zip"
  done
fi

if [ -n "${WILDFLY_SRC}" ];
then
  for fp in "${WILDFLY_FPS[@]}"
  do
      find "${WILDFLY_SRC}" -name "${fp}-*.zip" -print0 | while read -d $'\0' file
      do
          deploy "${file}" "org.wildfly" "${fp}" "zip"
      done
  done
fi

find "${GALLEON_PLUGIN_SRC}" -name "${GALLEON_PLUGINS_JAR}-*.jar" -print0 | while read -d $'\0' file
do
    deploy "${file}" "org.wildfly.galleon-plugins" "${GALLEON_PLUGINS_JAR}" "jar"
done

mkdir tmp
mvn org.apache.maven.plugins:maven-dependency-plugin:2.8:get -Dartifact=org.jboss.universe.producer:wildfly-producers:1.3.1.Final
cp "${LOCAL_MVN_REPO}"/org/jboss/universe/producer/wildfly-producers/1.3.1.Final/wildfly-producers-1.3.1.Final.jar tmp/
mvn org.apache.maven.plugins:maven-dependency-plugin:2.8:get -Dartifact=org.jboss.universe:community-universe:1.2.0.Final
cp "${LOCAL_MVN_REPO}"/org/jboss/universe/community-universe/1.2.0.Final/community-universe-1.2.0.Final.jar tmp/

mvn deploy:deploy-file -Dfile=../wildfly-producers-1.3.1.Final.jar -DgroupId=org.jboss.universe.producer -DartifactId=wildfly-producers -Dversion=1.3.1.Final -Dpackaging=jar -Durl=http://localhost:8081/repository/galleon -DrepositoryId=nexus2
mvn deploy:deploy-file -Dfile=../community-universe-1.2.0.Final.jar -DgroupId=org.jboss.universe -DartifactId=community-universe -Dversion=1.2.0 -Dpackaging=jar -Durl=http://localhost:8081/repository/galleon -DrepositoryId=nexus2
rm -rf tmp
