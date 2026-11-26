SELECT 'CREATE DATABASE auth_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'auth_db')\gexec
SELECT 'CREATE DATABASE rates_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'rates_db')\gexec
SELECT 'CREATE DATABASE conversions_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'conversions_db')\gexec
SELECT 'CREATE DATABASE alerts_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'alerts_db')\gexec
SELECT 'CREATE DATABASE notifications_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'notifications_db')\gexec
