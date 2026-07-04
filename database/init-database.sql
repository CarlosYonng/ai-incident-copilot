CREATE DATABASE IF NOT EXISTS incident_copilot
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'incident_copilot'@'%' IDENTIFIED BY 'incident_copilot123';

GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES, DROP
  ON incident_copilot.* TO 'incident_copilot'@'%';

FLUSH PRIVILEGES;
