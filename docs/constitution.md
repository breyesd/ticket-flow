# Constitución — ticket-flow

Principios innegociables. Toda spec, plan y tarea debe cumplirlos.

1. **Simplicidad primero**: Java 21 (LTS) y solo bibliotecas confiables, sin issues de seguridad y que ayuden a no generar código extra si ya existe en una biblioteca sólida.
2. **La spec manda**: ningún comportamiento se implementa si no está en la spec activa. Las specs viven en `docs/specs/`; solo la activa manda. Cambiar una decisión exige actualizar la spec antes. Si falta una decisión, se detiene el trabajo y se pregunta.
3. **Lógica separada de interfaz**: el núcleo (core) no lee de consola. Todo el core es testeable sin la CLI.
4. **Tests como puerta**: cada tarea termina con sus tests en verde, cubriendo el comportamiento relevante. Prohibido avanzar con tests en rojo.
5. **Calidad**: el código debe cumplir el estilo definido en el proyecto (formato/checkstyle) y pasar los chequeos de calidad.
6. **Seguridad**: el análisis de dependencias no reporta CVEs y se revisan las prácticas seguras del código generado.
7. **Control de versiones**: commits atómicos y con mensajes claros. Prohibido subir secretos o credenciales.
8. **Idioma**: código e identificadores en inglés; mensajes al usuario y documentación en español.

## Definición de Hecho

Una tarea solo está terminada cuando cumple **todo** lo siguiente:

- La spec activa está actualizada si el comportamiento cambió.
- Los tests están en verde.
- La calidad del código fue revisada.
- La seguridad fue revisada.
- La documentación fue actualizada.