#!/usr/bin/env bash
set -euo pipefail
for service in inventory orders delivery gateway keycloak; do
  variable="${service^^}_DB_PASSWORD"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
    -v db_user="$service" -v db_name="${service}_db" -v db_password="${!variable}" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'db_user', :'db_password')
WHERE NOT EXISTS(SELECT FROM pg_roles WHERE rolname=:'db_user')\gexec
SELECT format('CREATE DATABASE %I OWNER %I', :'db_name', :'db_user')
WHERE NOT EXISTS(SELECT FROM pg_database WHERE datname=:'db_name')\gexec
SELECT format('REVOKE ALL ON DATABASE %I FROM PUBLIC', :'db_name')\gexec
SELECT format('GRANT CONNECT ON DATABASE %I TO %I', :'db_name', :'db_user')\gexec
SQL
done
