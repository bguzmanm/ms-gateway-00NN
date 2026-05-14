# API Gateway con Spring Cloud Gateway WebMvc

## 📋 Descripción General

Este proyecto es un **API Gateway** construido con Spring Cloud Gateway WebMvc que actúa como punto de entrada único para una arquitectura de microservicios. Su rol principal es:

1. **Enrutamiento inteligente**: Dirige las solicitudes HTTP a diferentes microservicios según la ruta
2. **Autenticación centralizada**: Valida tokens JWT antes de permitir acceso a servicios protegidos
3. **Punto de control único**: Facilita la gestión de seguridad, logs y políticas en un solo lugar

---

## Arquitectura del Proyecto

```
Cliente HTTP
    ↓
Gateway (Puerto 80) ← Punto de entrada
    ├─→ Ruta /api/v1/auth/** → Servicio Auth (Puerto 8080) [Sin autenticación]
    └─→ Ruta /api/v1/doctors/** → Servicio Doctors (Puerto 8081) [Con JWT]
```

---

## Componentes Clave

### 1. **GatewayApplication.java**

```java
@SpringBootApplication
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
```

- Punto de entrada de la aplicación
- Habilita automáticamente todas las funcionalidades de Spring Boot

### 2. **application.yml** - Configuración de Rutas

Define cómo se enrutan las solicitudes:


| Ruta                                      | Destino        | Descripción                     |
| ----------------------------------------- | -------------- | -------------------------------- |
| /api/v1/auth/login, /api/v1/auth/register | localhost:8080 | Rutas públicas (sin filtro JWT) |
| /api/v1/doctors/**                        | localhost:8081 | Rutas protegidas (requieren JWT) |

```yml
spring:
  cloud:
    gateway:
      server:
        webmvc:
          routes:
            - id: auth-public
              uri: http://127.0.0.1:8080
              predicates:
                - Path=/api/v1/auth/login, /api/v1/auth/register

            - id: doctor-service
              uri: http://127.0.0.1:8081
              predicates:
                - Path=/api/v1/doctors/**
              filters:
                - jwtFilter  # ← Aplica el filtro de autenticación
```

**Conceptos clave**:

- Predicates: Condiciones que determinan si una ruta coincide (ej: Path)
- Filters: Procesan la solicitud antes de enviarla al microservicio (ej: jwtFilter)

### 3. JwtWebMvcFilter.java - Filtro de Autenticación

```java
@Component
public class JwtWebMvcFilter implements HandlerFilterFunction {

    @Override
    public ServerResponse filter(ServerRequest request, HandlerFunction<ServerResponse> next) {
        // 1. Extrae el token del header "Authorization"
        String authHeader = request.headers().firstHeader("Authorization");

        // 2. Valida que tenga formato "Bearer <token>"
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ServerResponse.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 3. Verifica la firma del token con la clave secreta
        String token = authHeader.substring(7);
        Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token);

        // 4. Si es válido, permite que continúe la solicitud
        return next.handle(request);
    }
}
```

Flujo del filtro:

```
Solicitud HTTP
    ↓
¿Tiene header "Authorization"? → NO → 401 Unauthorized
    ↓ SÍ
¿Empieza con "Bearer "? → NO → 401 Unauthorized
    ↓ SÍ
¿Token válido (firma correcta)? → NO → 401 Unauthorized
    ↓ SÍ
✅ Permite acceso al microservicio destino
```

### 4. GatewayConfig.java - Configuración del Filtro

```java
@Configuration
public class GatewayConfig {

    @Bean
    public FilterSupplier jwtFilterSupplier() {
        // Expone el filtro JWT como un bean disponible en YAML
        return () -> List.of(GatewayConfig.class.getMethod("jwtFilter"));
    }

    public static HandlerFilterFunction jwtFilter() {
        return jwtWebMvcFilterInstance;
    }
}
```

**Propósito**: Permite que el YAML haga referencia al filtro con jwtFilter

---

## 🚀 Cómo Funciona un Flujo Completo

Ejemplo 1: Login (sin autenticación)

```shell
# Cliente envía:
curl -X POST http://localhost/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"user": "admin", "pass": "123"}'

# Gateway recibe → Verifica que sea /api/v1/auth/login
# ✓ No aplica jwtFilter → Envía directamente a localhost:8080
# Recibe token JWT como respuesta
```

Ejemplo 2: Obtener doctores (requiere autenticación)

```shell
# Cliente envía:
curl -X GET http://localhost/api/v1/doctors \
  -H "Authorization: Bearer eyJhbGc..."

# Gateway recibe → Verifica que sea /api/v1/doctors/**
# ✓ Aplica jwtFilter → Valida token
# Si OK → Envía a localhost:8081
# Si falla → Retorna 401 Unauthorized
```

## Seguridad

Clave JWT secreta (configurada en application.yml):

```yml
app:
  jwt:
    secret: VGhpcy1Jcy1BLVZlcnktU2VjdXJlLVNlY3JldC1LZXktRm9yLUpXVC1BcGktQXV0aGVudGljYXRpb24=
```

- Se usa para validar la firma de los tokens
- Importante: Cambiar en producción, no dejarla en el código

## Ventajas de esta Arquitectura


| Ventaja                          | Descripción                                                 |
| -------------------------------- | ------------------------------------------------------------ |
| Centralización                  | Un único punto de entrada para todos los clientes           |
| Seguridad                        | Autenticación validada antes de llegar a los microservicios |
| Separación de responsabilidades | Cada servicio se enfoca en su lógica, no en autenticación  |
| Escalabilidad                    | Fácil agregar nuevas rutas o servicios                      |
| Logs y monitoreo                 | Punto único para registrar todas las solicitudes            |

## Dependencias Principales

```xml
<!-- Gateway WebMvc -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-gateway-server-webmvc</artifactId>
</dependency>

<!-- JWT para validación de tokens -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.3</version>
</dependency>

<!-- Spring Web -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

## Probando el Gateway

1. Inicia los servicios microservicios en puertos 8080 y 8081
2. Ejecuta el gateway: mvn spring-boot:run (puerto 80)
3. Prueba rutas públicas: curl http://localhost/api/v1/auth/login -v
4. Prueba rutas protegidas sin token: curl http://localhost/api/v1/doctors -v (debe retornar 401)
5. Prueba con token válido: curl http://localhost/api/v1/doctors -H "Authorization: Bearer <token>" -v

## Conceptos Educativos

**¿Por qué usar un Gateway?**
**Sin Gateway** (Acoplamiento directo):

```
Cliente → Servicio Auth
       → Servicio Doctors
       → Servicio Payments
       → ...
```

**Problemas**: Clientes conocen todas las URLs, lógica de autenticación duplicada, difícil de mantener.
Con Gateway (Punto único de entrada):

```
Cliente → Gateway → Servicio Auth
                  → Servicio Doctors
                  → Servicio Payments
```

Beneficios: Un solo punto de entrada, seguridad centralizada, fácil de escalar.

---

Desarrollado para estudiantes DUOC
Este proyecto es un ejemplo educativo para aprender cómo funcionan los API Gateways en arquitecturas de microservicios.
