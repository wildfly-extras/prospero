#!/bin/bash
readonly VERSION="4.1.79.Final"

curl -k https://repo1.maven.org/maven2/io/netty/netty-transport-native-epoll/${VERSION}/netty-transport-native-epoll-${VERSION}-linux-x86_64.jar -o netty-transport-native-epoll-${VERSION}-linux-x86_64.jar
curl -k https://repo1.maven.org/maven2/io/netty/netty-transport-native-epoll/${VERSION}/netty-transport-native-epoll-${VERSION}-linux-aarch_64.jar -o netty-transport-native-epoll-${VERSION}-linux-aarch_64.jar

mvn deploy:deploy-file -Dfile=netty-transport-native-epoll-${VERSION}-linux-x86_64.jar -Durl="file:$(pwd)/test-repo" -DgroupId=io.netty -DartifactId=netty-transport-native-epoll -Dpackaging=jar -Dclassifier=linux-x86_64 -Dversion=${VERSION}

mvn deploy:deploy-file -Dfile=netty-transport-native-epoll-${VERSION}-linux-aarch_64.jar -Durl="file:$(pwd)/test-repo" -DgroupId=io.netty -DartifactId=netty-transport-native-epoll -Dpackaging=jar -Dclassifier=linux-aarch_64 -Dversion=${VERSION}

rm netty-transport-native-epoll-${VERSION}-linux-x86_64.jar netty-transport-native-epoll-${VERSION}-linux-aarch_64.jar
