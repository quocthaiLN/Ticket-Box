### Docker

```
cd backend
docker-compose up -d
```

### Build
```
cd backend
./mvnw compile
```

### Migration

- Tự động chạy khi Run nhờ `flywaydb`

### Run

#### Backend
```
cd backend
./mvnw spring-boot:run
```

#### Frontend
```
cd frontend
npm install
npm run build
npm run dev
```