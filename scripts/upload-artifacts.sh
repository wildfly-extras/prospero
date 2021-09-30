#!/bin/bash

readonly REPO_ID=${REPO_ID:-"nexus2"}
readonly REPO_URL=${REPO_URL:-"http://localhost:8081/repository/updates"}
readonly LOCAL_MVN_REPO=${LOCAL_MVN_REPO:-"${HOME}/.m2/repository"}

deploy() {
  echo "Deploying from ${1}"
  if [ ! -f "${1}" ];
  then
    "Artifact list ${1} not found" 
    exit 1
  fi

  while IFS= read -r line; do
    if [[ "${line}" == *"pom" ]] || [[ "${line}" == *"zip" ]];
    then
      continue
    fi
    parts=($(echo $line | tr "," "\n"))
    path="${parts[1]}"

    parts=($(echo $path | tr "/" "\n"))
    len="${#parts[@]}"
    filename="${parts[${len}-1]}"
    version="${parts[${len}-2]}"
    artifact_id="${parts[${len}-3]}"
    group_id=$(IFS=. ; echo "${parts[*]:0:${len}-3}")
    packaging="jar"
    
    if [ "${artifact_id}-${version}.jar" != "${filename}" ];
    then
      classifier="${filename/${artifact_id}-${version}-/}"
      classifier="${classifier/.jar/}"
    else
      classifier=""
    fi

    cp "${LOCAL_MVN_REPO}${path}" "tmp/"
    mvn deploy:deploy-file -Dfile="tmp/${filename}" \
       -DrepositoryId="${REPO_ID}" -Durl="${REPO_URL}" \
       -DartifactId="${artifact_id}" -DgroupId="${group_id}" \
       -Dversion="${version}" -Dpackaging="${packaging}" \
       -Dclassifier="${classifier}"

  done < "${1}"
}

if [ -z "${WILDFLY_SRC}" ] && [ -z "${WILDFLY_CORE_SRC}" ];
then
  echo "At least one of (WILDFLY_SRC, WILDFLY_CORE_SRC) env variables need to be set"
  exit 1
fi

mkdir "tmp"
if [ -n "${WILDFLY_SCR}" ];
then
  deploy $(realpath "${WILDFLY_SRC}/ee-feature-pack/galleon-feature-pack/target"/*-artifact-list.txt)
  deploy $(realpath "${WILDFLY_SRC}/galleon-pack/galleon-feature-pack/target"/*-artifact-list.txt)
fi
if [ -n "${WILDFLY_CORE_SRC}" ];
then
  deploy $(realpath "${WILDFLY_CORE_SRC}/core-feature-pack/galleon-feature-pack/target"/*-artifact-list.txt)
fi
rm -rf "tmp"
