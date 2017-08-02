CREATE EXTENSION IF NOT EXISTS "postgis";

CREATE TABLE groups (
  id          UUID PRIMARY KEY,
  tenant_id   UUID NOT NULL,
  name        VARCHAR(100) NOT NULL CHECK (length(name) > 0),
  description TEXT
);
CREATE INDEX groups_tenant_id
  ON groups (tenant_id);
CREATE UNIQUE INDEX groups_name_unique
  ON groups (tenant_id, name);


CREATE TABLE charge_boxes (
  id                   UUID PRIMARY KEY,
  tenant_id            UUID NOT NULL,
  serial               VARCHAR(50)  NOT NULL CHECK (length(serial) > 0),
  gps_lat              NUMERIC      NOT NULL CHECK (gps_lat BETWEEN -90 AND 90),
  gps_lon              NUMERIC      NOT NULL CHECK (gps_lon BETWEEN -180 AND 180),
  address_line_1       VARCHAR(255) NOT NULL CHECK (length(address_line_1) > 0),
  address_line_2       VARCHAR(255),
  city                 VARCHAR(255) NOT NULL CHECK (length(city) > 0),
  country_iso          VARCHAR(3)   NOT NULL CHECK (length(country_iso) = 3),
  last_known_ip        INET,
  last_connected_at    TIMESTAMP,
  last_disconnected_at TIMESTAMP
);
CREATE INDEX charge_boxes_tenant_id
  ON charge_boxes (tenant_id);
CREATE UNIQUE INDEX charge_boxes_serial_unique
  ON charge_boxes (tenant_id, serial);


CREATE TABLE charge_boxes_groups (
  charge_box_id UUID NOT NULL REFERENCES charge_boxes,
  group_id      UUID NOT NULL REFERENCES groups
);
CREATE UNIQUE INDEX charge_boxes_groups_unique
  ON charge_boxes_groups (charge_box_id, group_id);


CREATE TYPE current_type AS ENUM ('AC', 'DC');


CREATE TABLE evses (
  id              VARCHAR(50)  NOT NULL,
  tenant_id       UUID NOT NULL,
  connector_id    SMALLINT     NOT NULL CHECK (connector_id > 0),
  charge_box_id   UUID         NOT NULL REFERENCES charge_boxes,
  current_type    current_type NOT NULL,
  max_power_watts INT          NOT NULL CHECK (max_power_watts > 0)
);

CREATE INDEX evses_tenant_id
  ON evses (tenant_id);
CREATE UNIQUE INDEX evses_id_tenant_id_unique
  ON evses (tenant_id, id);
CREATE UNIQUE INDEX evses_charge_box_id_connector_id_unique
  ON evses (charge_box_id, connector_id);

CREATE TABLE unregistered_charge_boxes (
  id                   UUID PRIMARY KEY,
  tenant_id            UUID NOT NULL,
  serial               VARCHAR(50) NOT NULL CHECK (length(serial) > 0),
  last_known_ip        INET,
  last_connected_at    TIMESTAMP,
  last_disconnected_at TIMESTAMP
);
CREATE INDEX unregistered_charge_boxes_tenant_id
  ON unregistered_charge_boxes (tenant_id);
CREATE UNIQUE INDEX unregistered_charge_boxes_serial_unique
  ON charge_boxes (tenant_id, serial);


CREATE TABLE socket_types (
  id          TEXT NOT NULL PRIMARY KEY,
  description TEXT NOT NULL CHECK (length(description) > 0)
);

INSERT INTO socket_types (id, description) VALUES
  ('Avcon', 'Avcon connector'),
  ('Domestic', 'Domestic plug'),
  ('Industrial2PDc', 'IEC60309_2P 60309 Industrial 2P (DC)'),
  ('IndustrialPneAc', 'IEC60309_PNE 60309 Industrial P + N + E (AC)'),
  ('Industrial3PEAc', 'IEC60309_3PE 60309 Industrial 3P + E (AC)'),
  ('Industrial3PENAc', 'IEC60309_3PEN 60309 Industrial 3P + E + N (AC)'),
  ('Type1', 'IEC62196_1 Type 1 Yazaki'),
  ('Type2', 'IEC62196_2 Type 2 Mennekes'),
  ('Type3', 'IEC62196_3 Type 3 Scame'),
  ('Type1Combo', 'Combo coupler based on SAEJ1772 connector'),
  ('Type2Combo', 'Combo coupler based on IEC62196_2 connector'),
  ('LPI', 'Large Paddle Inductive'),
  ('Nema520', 'NEMA5_20'),
  ('SPI', 'Small Paddle Inductive'),
  ('CHAdeMO', 'Tepco CHAdeMO fast charging'),
  ('Tesla', 'Tesla connector');

CREATE TABLE sockets (
  evse_id         VARCHAR(50) NOT NULL,
  tenant_id       UUID NOT NULL,
  type_id         TEXT        NOT NULL REFERENCES socket_types (id),
  cable_attached  BOOLEAN     NOT NULL,
  max_power_watts INT CHECK (max_power_watts > 0),
  FOREIGN KEY (evse_id, tenant_id) REFERENCES evses (id, tenant_id)
);
