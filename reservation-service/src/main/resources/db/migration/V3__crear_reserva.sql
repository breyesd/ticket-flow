-- Migración V3: tabla `reserva` para la fase de pago y persistencia
-- (spec 0001, secciones 2.4 y 3.2; plan Fase 3, F3.T1).
--
-- Modela la reserva resultante de una confirmación de compra. La reserva
-- se crea únicamente en el flujo `confirmar`, directamente con estado
-- PAGADA (pago exitoso) o FALLIDA (pago rechazado); no existe estado
-- PENDIENTE persistido (spec 0001, sección 2.4).

CREATE TABLE reserva (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    asiento_id BIGINT NOT NULL,
    monto DECIMAL(10, 2) NOT NULL,
    estado VARCHAR(20) NOT NULL,
    fecha_creacion TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_reserva_asiento FOREIGN KEY (asiento_id) REFERENCES asiento (id),
    CONSTRAINT ck_reserva_estado CHECK (estado IN ('PAGADA', 'FALLIDA')),
    CONSTRAINT ck_reserva_monto_positivo CHECK (monto > 0)
);

CREATE INDEX idx_reserva_asiento ON reserva (asiento_id);
CREATE INDEX idx_reserva_usuario ON reserva (usuario_id);

-- Refuerzo de consistencia frente al double-booking: a lo sumo una
-- reserva PAGADA por asiento (spec 0001, sección 3.2; plan Fase 3,
-- F3.T1).
--
-- En lugar de un índice único parcial (no portable a H2, usado en el
-- perfil `test`), se añade una columna marcadora `asiento_pagada_id`
-- que almacena el asiento sólo cuando la reserva es PAGADA y queda en
-- NULL cuando es FALLIDA. Tanto PostgreSQL como H2 permiten múltiples
-- NULL en una columna con restricción UNIQUE, de modo que varias
-- reservas FALLIDA pueden coexistir pero sólo una PAGADA por asiento.
ALTER TABLE reserva ADD COLUMN asiento_pagada_id BIGINT;

ALTER TABLE reserva
    ADD CONSTRAINT uk_reserva_asiento_pagada UNIQUE (asiento_pagada_id);

ALTER TABLE reserva
    ADD CONSTRAINT fk_reserva_asiento_pagada
        FOREIGN KEY (asiento_pagada_id) REFERENCES asiento (id);