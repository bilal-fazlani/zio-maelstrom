#!/usr/bin/env fish

# This script talks to one password to get credentials
# and then uses those credentials to authenticate with github
# and then installs the requirements for mkdocs-material-insiders

op signin
# set username
set -x GH_USERNAME (op read op://private/mkdocs-material-insiders/username)
# set password
set -x GH_TOKEN (op read op://private/mkdocs-material-insiders/credential)

pip install -r requirements.txt
