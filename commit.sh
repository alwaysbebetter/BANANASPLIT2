#!/bin/bash 
# git config --global credential.helper 'cache --timeout=604800'
git add *
git commit -m "$1"
git push A master
