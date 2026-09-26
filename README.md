### Docker

```
docker compose up -d postgres redis rabbitmq minio nginx
```

Nginx listens on `localhost:8080` and forwards to the local backend on
`127.0.0.1:8082`. Set `PORT=8082` in the untracked `backend/.env` before
starting Spring Boot. The frontend's Vite proxy and local Postman environments
continue to use port `8080`. To expose the gateway for VNPay IPN, run
`docker compose up -d ngrok`; the tunnel targets Nginx, not Spring Boot.
The IP limits and bursts can be adjusted in `nginx/nginx.conf` and reloaded
with `docker compose restart nginx`.
With Redis running locally, use `REDIS_TEST_PORT=6379 ./mvnw test` from
`backend/` to include the Redis rate-limit integration tests.

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
