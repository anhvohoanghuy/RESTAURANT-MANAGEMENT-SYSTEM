# feat1 — Hệ thống quản lý nhà hàng

Hệ thống quản lý nhà hàng: catalog thực đơn, bàn ăn và đặt chỗ, đặt món, phiếu bếp,
quản lý kho theo công thức (BOM) với đặt trước tồn kho, và thanh toán.

Backend là một **modular monolith** Spring Boot tổ chức theo các bounded context DDD; các
context dùng chung một database và một deployable nhưng tích hợp với nhau chủ yếu qua sự
kiện Kafka. Admin SPA viết bằng Vue 3 nằm riêng trong `admin-ui/`.

> Tài liệu kiến trúc đầy đủ (outbox giao dịch, idempotency, saga, mô hình bảo mật, nợ kỹ
> thuật) nằm ở **[ARCHITECTURE.md](ARCHITECTURE.md)**. Đọc file đó trước khi sửa code liên
> quan đến messaging hoặc bảo mật.

## Công nghệ

| Thành phần | Lựa chọn |
|---|---|
| Backend | Spring Boot 4.0.6, Java 17, Maven |
| Database | MySQL 8 (`ddl-auto=update`, **không có công cụ migration**) |
| Messaging | Kafka (`spring-kafka`), payload JSON |
| Cache | Redis (Lettuce) — refresh token, khóa đăng nhập, rate limiting |
| Auth | JWT (jjwt 0.12.5), BCrypt, Google OAuth2 |
| API docs | springdoc-openapi → `/swagger-ui.html` |
| Admin UI | Vue 3.5, vue-router 5, Vite 8, Vitest 4, TypeScript |

## Yêu cầu

- JDK 17 (đặt `JAVA_HOME`)
- Docker + Docker Compose (cho MySQL, Redis, Kafka)
- Node.js 20+ (cho admin-ui)

## Thiết lập nhanh

### 1. Khởi động hạ tầng

```bash
cp .env.example .env          # điều chỉnh nếu cần
docker compose up -d          # MySQL (3306), Redis (6379), Kafka (9092)
```

### 2. Seed dữ liệu local (tùy chọn)

```bash
docker exec -i feat1-mysql mysql -uroot -p"$DB_PASSWORD" mydb < scripts/dev-seed.sql
```

Script tạo sẵn tài khoản admin/staff và an toàn khi chạy nhiều lần.

### 3. Chạy backend

```bash
export DB_PASSWORD=123456      # BẮT BUỘC — không có giá trị mặc định, app fail-fast nếu thiếu
./mvnw spring-boot:run
```

- API: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

### 4. Chạy admin UI

```bash
cd admin-ui
npm install
npm run dev                    # http://localhost:5173, gọi API tại VITE_API_BASE_URL (mặc định :8080)
```

## Cấu hình

Cấu hình runtime ở `src/main/resources/application.properties`, phần lớn có thể ghi đè
bằng biến môi trường. Các biến quan trọng:

| Biến | Mục đích | Ghi chú |
|---|---|---|
| `DB_USERNAME` / `DB_PASSWORD` | Thông tin MySQL | `DB_PASSWORD` **không có mặc định** |
| `JWT_SECRET` | Khóa ký JWT (≥32 byte) | Mặc định `change-me…` — **phải đặt lại cho production** |
| `KAFKA_BOOTSTRAP_SERVERS` | Broker Kafka | Mặc định `localhost:9092` |
| `GOOGLE_OAUTH_CLIENT_IDS` | Đăng nhập Google | Bỏ trống để tắt |
| `JPA_SHOW_SQL` | Log SQL | Mặc định `false` |

## Cấu trúc dự án

```text
src/main/java/com/example/feat1/
  DDD/
    auth/                đăng nhập (local + Google), JWT, session, rate limit
    identity_context/    user, credential, role, permission
    menu_context/        danh mục, món, topping, công thức (BOM)
    table_context/       khu vực, bàn, occupancy, session, đặt chỗ
    order_context/       giỏ hàng, đơn hàng, hủy đơn (hub điều phối)
    inventory_context/   nguyên liệu, tồn kho, đặt trước → giải phóng → quyết toán
    kitchen_context/     phiếu bếp và máy trạng thái
    payment_context/     thanh toán, QR, hoàn tiền
    shared/outbox/       transactional outbox
admin-ui/                Vue 3 admin SPA
ARCHITECTURE.md          tài liệu kiến trúc chi tiết
```

## Kiểm thử

```bash
./mvnw test               # backend (Spotless + google-java-format chạy ở phase validate)
cd admin-ui && npm test   # frontend (Vitest — chỉ test logic, chưa có test component/view)
```

## Ghi chú vận hành

- **Không có công cụ migration**: schema quản lý bằng `ddl-auto=update`, lịch sử schema chỉ
  nằm trong các entity class.
- **Kafka auto-create topics** đang bật ở compose local; production cần cân nhắc khai báo
  topic tường minh.
- Một số luồng chưa hoàn thiện end-to-end (ví dụ email xác thực / reset mật khẩu chỉ log,
  không gửi thật) — xem mục *Architectural Debt* trong [ARCHITECTURE.md](ARCHITECTURE.md).
