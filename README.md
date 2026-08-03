### Docker

```
cd backend
docker-compose up
```

### Build
```
cd backend
./mvnw compile
```

### Migration

- Tự động chạy khi Run nhờ `flywaydb`


### Run

```
cd backend
./mvnw spring-boot:run "-Dspring-boot.run.profiles=api"
./mvnw spring-boot:run "-Dspring-boot.run.profiles=worker"
./mvnw spring-boot:run "-Dspring-boot.run.profiles=api,worker"
```