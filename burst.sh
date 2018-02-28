#!/bin/bash

export FIREBIRD=$(dirname $0)/lib/firebird/$(getconf LONG_BIT)

MY_MAVEN_VERSION=3.5.0

MY_SELF=$0
MY_CMD=$1
MY_ARG=$2

function usage() {
    cat << EOF
usage: $0 [command] [arguments]

  h2shell                       open a H2 shell for DB manipulation
  help                          shows the help you just read
  compile                       compile jar and create docs using maven
  upgrade                       upgrade the config files to BRS format
EOF
}

function maybe_load_dump_usage () {
    if [ -z "$MY_ARG" ]; then
        usage
        exit 1
    fi
}

function upgrade_conf () {
    BRS_CFG_NAME="conf/nxt-default.properties"

    if [ -r $BRS_CFG_NAME ]
    then
        BRS=$(<$BRS_CFG_NAME)    # read in the config file content
        # P2P-related params
        BRS="${BRS//nxt\.shareMyAddress/P2P.shareMyAddress}"
        BRS="${BRS//nxt\.myAddress/P2P.myAddress}"
        BRS="${BRS//nxt\.peerServerHost/P2P.Listen}"
        BRS="${BRS//nxt\.peerServerPort/P2P.Port}"
        BRS="${BRS//nxt\.myPlatform/P2P.myPlatform}"
        BRS="${BRS//nxt\.wellKnownPeers/P2P.BootstrapPeers}"
        BRS="${BRS//burst\.rebroadcastPeers/P2P.rebroadcastTo}"
        BRS="${BRS//burst\.connectWellKnownFirst/P2P.NumBootstrapConnections}"
        BRS="${BRS//nxt\.knownBlacklistedPeers/P2P.BlacklistedPeers}"
        BRS="${BRS//nxt\.maxNumberOfConnectedPublicPeers/P2P.MaxConnections}"
        BRS="${BRS//nxt\.connectTimeout/P2P.TimeoutConnect_ms}"
        BRS="${BRS//nxt\.readTimeout/P2P.TimeoutRead_ms}"
        BRS="${BRS//nxt\.peerServerIdleTimeout/P2P.TimeoutIdle_ms}"
        BRS="${BRS//nxt\.blacklistingPeriod/P2P.BlacklistingTime_ms}"
        BRS="${BRS//nxt\.sendToPeersLimit/P2P.TxResendThreshold}"
        BRS="${BRS//nxt\.usePeersDb/P2P.usePeersDb}"
        BRS="${BRS//nxt\.savePeers/P2P.savePeers}"

        # P2P Hallmarks
        BRS="${BRS//nxt\.enableHallmarkProtection/P2P.HallmarkProtection}"
        BRS="${BRS//nxt\.myHallmark/P2P.myHallmark}"
        BRS="${BRS//nxt\.pushThreshold/P2P.HallmarkPush}"
        BRS="${BRS//nxt\.pullThreshold/P2P.HallmarkPull}"
        BRS="${BRS///}"
        BRS="${BRS///}"

        # JETTY pass-through params
        BRS="${BRS//nxt\.enablePeerServerDoSFilter/JETTY.P2P.DoSFilter}"
        BRS="${BRS//nxt\.peerServerDoSFilter.maxRequestsPerSec/JETTY.P2P.DoSFilter.maxRequestsPerSec}"
        BRS="${BRS//nxt\.peerServerDoSFilter.delayMs/JETTY.P2P.DoSFilter.delayMs}"
        BRS="${BRS//nxt\.peerServerDoSFilter.maxRequestMs/JETTY.P2P.DoSFilter.maxRequestMs}"

        # DEVELOPMENT-related params (TestNet, Offline, Debug, Timewarp etc.)
        BRS="${BRS//nxt\.isTestnet/DEV.TestNet}"
        BRS="${BRS//nxt\.testnetPeers/DEV.TestNet.Peers}"
        BRS="${BRS//nxt\.isOffline/DEV.Offline}"
        BRS="${BRS//nxt\.time.Multiplier/DEV.TimeWarp}"
        BRS="${BRS//burst\.mockMining/DEV.mockMining}"
        BRS="${BRS//nxt\.testDbUrl/DEV.DB.Url}"
        # that bug may be in
        BRS="${BRS//nxt\.testDUsername/DEV.DB.Username}"
        BRS="${BRS//nxt\.testDbUsername/DEV.DB.Username}"
        BRS="${BRS//nxt\.testDbPassword/DEV.DB.Password}"

        # API-related params
        BRS="${BRS//nxt\.enableAPIServer/}"
        BRS="${BRS///}"
        BRS="${BRS///}"
        
        # DB-related params
        BRS="${BRS//nxt\.dbUrl/DB.Url}"
        BRS="${BRS//nxt\.dbUsername/DB.Username}"
        BRS="${BRS//nxt\.dbPassword/DB.Password}"
        BRS="${BRS//nxt\.dbMaximumPoolSize/DB.Connections}"
        BRS="${BRS///}"
        BRS="${BRS///}"
        BRS="${BRS///}"
        BRS="${BRS///}"

        # GPU-related params
        BRS="${BRS//burst\.oclVerify/GPU.Acceleration}"
        BRS="${BRS//burst\.oclAuto/GPU.AutoDetect}"
        BRS="${BRS//burst\.oclPlatform/GPU.PlatformIdx}"
        BRS="${BRS//burst\.oclDevice/GPU.DeviceIdx}"
        BRS="${BRS//burst\.oclMemPercent/GPU.MemPercent}"
        BRS="${BRS//burst\.oclHashesPerEnqueue/GPU.HashesPerBatch}"
        
        BRS="${BRS///}"
        echo "$BRS" > conf/brs-default.properties.test
    else
        echo "$BRS_CFG_NAME not present or not readable."
        exit 1
    fi
}

if [ -z `which java 2>/dev/null` ]; then
    echo "please install java from eg. https://java.com/download/"
    exit 1
fi

if [[ $# -gt 0 ]] ; then
    case "$MY_CMD" in
        "compile")
            if [ -d "maven/apache-maven-${MY_MAVEN_VERSION}" ]; then
                PATH=maven/apache-maven-${MY_MAVEN_VERSION}/bin:$PATH
            fi

            ## check if command exists
            if hash mvn 2>/dev/null; then
                mvn package
                mvn javadoc:javadoc-no-fork
                rm -rf html/ui/doc
                mkdir -p html/ui/doc
                cp -r target/site/apidocs/* html/ui/doc
                cp dist/tmp/burst.jar .
                echo a .zip file has been built for distribution in dist/, its contents are in dist/tmp
                echo Nevertheless, now you can start the wallet with ./burst.sh
            else
                echo This build method is no longer supported. Please install maven.
                echo https://maven.apache.org/install.html
                if hash wget 2>/dev/null; then
                    read -p "Do you want me to install a local copy of maven in this directory? " -n 1 -r
                    echo
                    if [[ $REPLY =~ ^[Yy]$ ]]; then
                        mkdir -p maven
                        cd maven
                        ## This is an official mirror
                        wget "http://mirror.23media.de/apache/maven/maven-3/$MY_MAVEN_VERSION/binaries/apache-maven-${MY_MAVEN_VERSION}-bin.tar.gz"
                        tar -xvzf "apache-maven-${MY_MAVEN_VERSION}-bin.tar.gz"
                        rm "apache-maven-$MY_MAVEN_VERSION-bin.tar.gz"
                        echo Please try again, it should work now. You might want to check if the environment variable JAVA_HOME points to a valid JDK.
                    fi
                fi
            fi
            ;;
        "upgrade")
            upgrade_conf
            ;;
        "h2shell")
            java -cp burst.jar org.h2.tools.Shell
            ;;
        *)
            usage
            ;;
    esac
else
    java -cp burst.jar:conf brs.Burst
fi
