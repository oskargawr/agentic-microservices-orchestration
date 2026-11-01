#!/bin/bash
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "postgres" <<-EOSQL
    SELECT 'CREATE DATABASE temporal'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'temporal')\gexec
    SELECT 'CREATE DATABASE temporal_visibility'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'temporal_visibility')\gexec
    SELECT 'CREATE DATABASE business'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'business')\gexec
EOSQL

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "business" <<-EOSQL
    CREATE SCHEMA IF NOT EXISTS inventory;
    CREATE SCHEMA IF NOT EXISTS payment;
    CREATE SCHEMA IF NOT EXISTS shipping;
    COMMENT ON SCHEMA inventory IS 'TH-100 schema-per-service; tables in TH-102';
    COMMENT ON SCHEMA payment IS 'TH-100 schema-per-service; tables in TH-103';
    COMMENT ON SCHEMA shipping IS 'TH-100 schema-per-service; tables in TH-104';
EOSQL
