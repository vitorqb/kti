#!/bin/bash

if [ "$#" != "1" ]
then
    echo "Exactly one argument, the host, should be parsed" >/dev/stderr
    exit 1
fi

export HOST="$1"
export DEST="/kti/releases/$(date +%Y%m%d%H%M%S)"
export START="${DEST}/start.sh"
export DB="jdbc:sqlite:/kti/kti.db"

function install_java() {
    echo -e "\n\n INSTALLING JDK8 \n\n"
    ssh "$HOST" "add-apt-repository ppa:openjdk-r/ppa -y && apt-get update --assume-yes && apt-get install openjdk-8-jdk --assume-yes"
}

function install_nginx() {
    echo -e "\n\n INSTALLING NGINX... \n\n"
    ssh "$HOST" "apt-get update --assume-yes && apt-get install nginx --assume-yes"
}

function compile() {
    echo -e "\n\n COMPILING... \n\n"
    lein compile && lein uberjar
}

function prepare_release_dir() {
    echo -e "\n\n PREPARING RELEASE DIR (${DEST})... \n\n"
    ssh "$HOST" "mkdir -p '$DEST'"
}

function send_executable() {
    echo -e "\n\n COPYING JAR FILE... \n\n"
    scp ./target/uberjar/kti.jar "${HOST}:${DEST}/kti.jar"
}

function send_secrets_and_resources() {
    echo -e "\n\n COPYING SECRETS AND RESOURCES FILE... \n\n"
    scp ./secrets/prod-config.edn "${HOST}:${DEST}/prod-config.edn"
    scp -r ./resources "${HOST}:${DEST}/resources"
}

function make_start_script() {
    echo -e "\n\n PREPARING START SCRIPT... \n\n"
    cat <<EOF | ssh "$HOST" "cat >${START}"
#!/bin/bash
/usr/lib/jvm/java-8-openjdk-amd64/jre/bin/java -jar -Dconf=prod-config.edn kti.jar migrate
/usr/lib/jvm/java-8-openjdk-amd64/jre/bin/java -jar -Dconf=prod-config.edn kti.jar
EOF
    ssh "$HOST" "chmod +x ${START}"
}

function send_kti_systemd() {
    echo -e "\n\n SENDING SYSTEMD SERVICE... \n\n"
    cat ./scripts/templates/kti.service.template | envsubst | ssh "$HOST" "cat >/etc/systemd/system/kti.service"
}

function restart_kti_service() {
    echo -e "\n\n RESTARTING KTI SERVICE... \n\n"
    ssh "$HOST" "systemctl enable kti; systemctl restart kti"
}

function send_nginx_config() {
    echo -e "\n\n SENDING SYSTEMD SERVICE FOR NGINX... \n\n"
    cat ./scripts/templates/ngix.config.template | ssh "$HOST" "cat >/etc/nginx/sites-available/default"
}

function restart_kti_ngix_service() {
    echo -e "\n\n RESTARTING NGINX SERVICE... \n\n"
    ssh "$HOST" "systemctl enable nginx; systemctl restart nginx"
}

function clean_old_releases() {
    echo -e "\n\n CLEANING OLD RELEASES \n\n"
    ssh "$HOST" /bin/bash <<EOF
cd /kti/releases
ls | grep -P '^[0-9]{14}$' | sort --reverse | tail -n +5 | while read x
do
  echo "Removing \$x"
  rm -r "\$x"
done
EOF
}

compile \
    && prepare_release_dir \
    && install_java \
    && install_nginx \
    && send_executable \
    && send_secrets_and_resources \
    && make_start_script \
    && send_kti_systemd \
    && restart_kti_service \
    && send_nginx_config \
    && restart_kti_ngix_service \
    && clean_old_releases
