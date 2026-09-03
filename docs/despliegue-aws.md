# Despliegue en AWS — TicketFlow

Guía para desplegar los microservicios de TicketFlow en AWS usando
ECS Fargate (o App Runner como alternativa). Es documentación
orientativa del alcance de la spec 0001, sección 8: no es un entregable
bloqueante, pero describe los pasos necesarios para llevar la imagen
local a producción.

## Visión general

Dos servicios (más el módulo `common` embebido en cada jar):

| Servicio | Rol | Puertos | Servicios externos |
|----------|-----|---------|--------------------|
| `reservation-service` | Núcleo: locks, pago, ACID, público del evento | 8080 (HTTP) | PostgreSQL, Redis, Kafka |
| `notification-service` | Consumidor Kafka, simula envío de boleto | — | Kafka |

La infraestructura dependiente (PostgreSQL, Redis, Kafka) se asume
gestionada por AWS (RDS, ElastiCache, Amazon MSK) o por las imágenes
del `docker-compose.yml` para entornos no productivos.

## 1. Construcción de imágenes

Desde la raíz del repositorio:

```bash
docker build -f reservation-service/Dockerfile -t ticketflow-reservation-service:1.0.0 .
docker build -f notification-service/Dockerfile -t ticketflow-notification-service:1.0.0 .
```

Cada `Dockerfile` es multi-stage: compila con Eclipse Temurin JDK 21 y
ejecuta con un JRE 21 minimalista como usuario no-root (`app`).

## 2. Publicación en ECR

```bash
AWS_ACCOUNT=123456789012
AWS_REGION=us-east-1

# Autenticar Docker contra ECR
aws ecr get-login-password --region $AWS_REGION \
  | docker login --username AWS --password-stdin \
      $AWS_ACCOUNT.dkr.ecr.$AWS_REGION.amazonaws.com

# Crear repositorios (una vez)
aws ecr create-repository \
  --repository-name ticketflow/reservation-service --region $AWS_REGION
aws ecr create-repository \
  --repository-name ticketflow/notification-service --region $AWS_REGION

# Etiquetar y subir
docker tag ticketflow-reservation-service:1.0.0 \
  $AWS_ACCOUNT.dkr.ecr.$AWS_REGION.amazonaws.com/ticketflow/reservation-service:1.0.0
docker push \
  $AWS_ACCOUNT.dkr.ecr.$AWS_REGION.amazonaws.com/ticketflow/reservation-service:1.0.0

docker tag ticketflow-notification-service:1.0.0 \
  $AWS_ACCOUNT.dkr.ecr.$AWS_REGION.amazonaws.com/ticketflow/notification-service:1.0.0
docker push \
  $AWS_ACCOUNT.dkr.ecr.$AWS_REGION.amazonaws.com/ticketflow/notification-service:1.0.0
```

## 3. Variables de entorno

Ambos servicios leen su configuración de variables de entorno (los
valores por defecto apuntan a `localhost` para desarrollo local):

| Variable | Servicio | Descripción | Ejemplo |
|----------|----------|-------------|---------|
| `DB_URL` | reservation | URL JDBC de PostgreSQL | `jdbc:postgresql://<rds-host>:5432/ticketflow` |
| `DB_USER` | reservation | Usuario de PostgreSQL | `ticketflow` |
| `DB_PASSWORD` | reservation | Contraseña de PostgreSQL | (secreto) |
| `REDIS_HOST` | reservation | Host de ElastiCache Redis | `<cache-host>` |
| `REDIS_PORT` | reservation | Puerto de Redis | `6379` |
| `KAFKA_BOOTSTRAP_SERVERS` | ambos | Brokers de MSK/Kafka | `<msk-bootstrap>:9092` |

## 4. ECS Fargate

### Task definition (reservation-service, resumen)

```json
{
  "family": "ticketflow-reservation-service",
  "networkMode": "awsvpc",
  "cpu": "512",
  "memory": "1024",
  "requiresCompatibilities": ["FARGATE"],
  "executionRoleArn": "<ecs-execution-role>",
  "taskRoleArn": "<ecs-task-role>",
  "containerDefinitions": [
    {
      "name": "reservation-service",
      "image": "<account>.dkr.ecr.<region>.amazonaws.com/ticketflow/reservation-service:1.0.0",
      "portMappings": [{ "containerPort": 8080, "protocol": "tcp" }],
      "secrets": [
        { "name": "DB_PASSWORD", "valueFrom": "<arn-del-secreto-en-secrets-manager>" }
      ],
      "environment": [
        { "name": "DB_URL", "value": "jdbc:postgresql://<rds-host>:5432/ticketflow" },
        { "name": "DB_USER", "value": "ticketflow" },
        { "name": "REDIS_HOST", "value": "<cache-host>" },
        { "name": "REDIS_PORT", "value": "6379" },
        { "name": "KAFKA_BOOTSTRAP_SERVERS", "value": "<msk-bootstrap>:9092" }
      ]
    }
  ]
}
```

El `notification-service` usa una task definition análoga, sin
`portMappings` (no expone HTTP) y con la variable
`KAFKA_BOOTSTRAP_SERVERS`.

### Registro y despliegue

```bash
aws ecs register-task-definition \
  --cli-input-json file://reservation-task-definition.json

aws ecs create-service \
  --cluster ticketflow \
  --service-name reservation-service \
  --task-definition ticketflow-reservation-service \
  --desired-count 2 \
  --launch-type FARGATE \
  --network-configuration \
  "awsvpcConfiguration={subnets=[<subnet-ids>],securityGroups=[<sg-id>],assignPublicIp=DISABLED}"
```

## 5. Alternativa: App Runner

App Runner es adecuado para el `reservation-service` (servicio web con
peticiones HTTP) cuando no se necesite el control fino de ECS:

```bash
aws apprunner create-service \
  --service-name ticketflow-reservation \
  --source-configuration '{
    "ImageRepository": {
      "ImageIdentifier": "<account>.dkr.ecr.<region>.amazonaws.com/ticketflow/reservation-service:1.0.0",
      "ImageRepositoryType": "ECR",
      "ImageConfiguration": {
        "Port": "8080",
        "RuntimeEnvironmentVariables": {
          "DB_URL": "jdbc:postgresql://<rds-host>:5432/ticketflow",
          "DB_USER": "ticketflow",
          "REDIS_HOST": "<cache-host>",
          "KAFKA_BOOTSTRAP_SERVERS": "<msk-bootstrap>:9092"
        }
      }
    }
  }'
```

> App Runner no es adecuado para el `notification-service` (consumidor
> puro sin endpoint): usa ECS Fargate para ese servicio.

## 6. Consideraciones de seguridad y operación

- **Secretos** (`DB_PASSWORD`, credenciales de Kafka): usar AWS Secrets
  Manager referenciados desde la task definition; nunca en variables de
  entorno planas ni en el repositorio (constitución §7).
- **VPC**: los servicios dependientes (RDS, ElastiCache, MSK) deben ser
  accesibles desde la subred/VPC del servicio; el `reservation-service`
  se expone tras un ALB con listener 80/443 → target group 8080.
- **Healthcheck**: el ALB puede apuntar a un endpoint simple (p. ej.
  `/actuator/health` si se habilita el actuador, o el propio
  `GET /api/v1/eventos/{id}/funciones/{id}/asientos`).
- **Escalado**: `reservation-service` escala por CPU/peticiones;
  `notification-service` escala por lag del consumer de Kafka.
- **Aislamiento**: `reservation-service` es el único con acceso a
  PostgreSQL y Redis; `notification-service` solo necesita Kafka.