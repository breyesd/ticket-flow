-- Inserta 1 evento semilla "Concierto Demo".
INSERT INTO evento (nombre, descripcion) VALUES
    ('Concierto Demo', 'Evento semilla de TicketFlow con 100 asientos.');

-- Inserta 1 función asociada al evento sembrado. La fecha concreta
-- del seed no forma parte del contrato del seed; basta con que sea
-- un timestamp válido. Se usa CAST para fijar la fecha explícitamente
-- y mantener el SQL portable entre PostgreSQL y H2 (la columna es
-- TIMESTAMP WITH TIME ZONE y ningún operador de suma es portable
-- entre los dos motores).
INSERT INTO funcion (evento_id, fecha_hora)
SELECT id, CAST('2099-01-01 20:00:00+00' AS TIMESTAMP WITH TIME ZONE)
FROM evento
WHERE nombre = 'Concierto Demo'
ORDER BY id DESC
LIMIT 1;

-- Inserta 100 asientos (numerados del 1 al 100) en la función semilla,
-- todos en estado DISPONIBLE. Se usa una CTE recursiva para generar la
-- serie 1..100 de forma portable entre PostgreSQL y H2 (no se depende
-- de generate_series).
WITH RECURSIVE numeros(n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM numeros WHERE n < 100
)
INSERT INTO asiento (funcion_id, numero, estado)
SELECT f.id, n.n, 'DISPONIBLE'
FROM funcion f
CROSS JOIN numeros n
WHERE f.evento_id = (SELECT id FROM evento WHERE nombre = 'Concierto Demo' ORDER BY id DESC LIMIT 1);
