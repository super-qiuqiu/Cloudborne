#!/usr/bin/env bash
set -euo pipefail

version="1.13.15"
repo="${HOME}/.m2/repository/io/nekohasekai/libbox/${version}"
mkdir -p "${repo}"
cp vendor/sing-box/libbox.aar "${repo}/libbox-${version}.aar"
cat > "${repo}/libbox-${version}.pom" <<EOF
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>io.nekohasekai</groupId>
  <artifactId>libbox</artifactId>
  <version>${version}</version>
  <packaging>aar</packaging>
</project>
EOF
printf 'Published libbox %s to %s\n' "${version}" "${repo}"
