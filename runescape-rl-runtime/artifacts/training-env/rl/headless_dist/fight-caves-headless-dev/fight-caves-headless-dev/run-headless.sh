#!/usr/bin/env bash
title="Fight Caves Headless"
echo -e '\033]2;'$title'\007'
java -jar fight-caves-headless.jar
if [ $? -ne 0 ]; then
  echo "Error: The Java application exited with a non-zero status."
  read -p "Press enter to continue..."
fi
