#!/usr/bin/env bash
perl -p -i -e 's/\$\{([^}]+)\}/defined $ENV{$1} ? $ENV{$1} : $&/eg' < conf/server_tpl.xml > conf/server.xml
exec ${CATALINA_HOME}/bin/catalina.sh run
