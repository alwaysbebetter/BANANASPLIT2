#!/bin/usr/bash
javac fr/upem/net/tcp/*/*.java
case $1 in
	1) rlwrap java fr/upem/net/tcp/server/ServerMultiChatTCPNonBlockingWithQueueGoToMatou3 7777 ;;
	2) rlwrap java fr/upem/net/tcp/client/ClientTCPMatou localhost 7777 ;;
esac
