#!/bin/bash

# Script to sync web/index.html to static resources
echo "Syncing web/index.html to static resources..."

# Copy web/index.html to static resources
cp web/index.html api/src/main/resources/static/index.html

echo "Sync completed!"
echo "You can now restart the server with: cd api && ./gradlew run"
