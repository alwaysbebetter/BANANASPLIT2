#!/bin/usr/bash
javac fr/upem/net/tcp/*/*.java
case $1 in
	1) java fr/upem/net/tcp/server/ServerMultiChatTCPNonBlockingWithQueueGoToMatou3 7777 ;;
	2) java fr/upem/net/tcp/client/ClientTCPMatou localhost 7777 ;;
esac
