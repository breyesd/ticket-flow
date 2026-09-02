CREATE TABLE evento (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(200) NOT NULL,
    descripcion VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE funcion (
    id BIGSERIAL PRIMARY KEY,
    evento_id BIGINT NOT NULL,
    fecha_hora TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_funcion_evento FOREIGN KEY (evento_id) REFERENCES evento (id)
);

CREATE INDEX idx_funcion_evento ON funcion (evento_id);

CREATE TABLE asiento (
    id BIGSERIAL PRIMARY KEY,
    funcion_id BIGINT NOT NULL,
    numero INTEGER NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'DISPONIBLE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_asiento_funcion FOREIGN KEY (funcion_id) REFERENCES funcion (id),
    CONSTRAINT uk_asiento_funcion_numero UNIQUE (funcion_id, numero),
    CONSTRAINT ck_asiento_estado CHECK (estado IN ('DISPONIBLE', 'VENDIDO'))
);

CREATE INDEX idx_asiento_funcion ON asiento (funcion_id);
