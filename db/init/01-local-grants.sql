-- LAPTOP ONLY. The mysql image conjures `appuser` out of MYSQL_USER/MYSQL_PASSWORD, but it
-- grants privileges on MYSQL_DATABASE alone — so without this line the sidecar's own schema
-- would exist and still be unreachable.
GRANT ALL PRIVILEGES ON neo_08.* TO 'appuser'@'%';
GRANT ALL PRIVILEGES ON sidecar_db.* TO 'appuser'@'%';
FLUSH PRIVILEGES;
