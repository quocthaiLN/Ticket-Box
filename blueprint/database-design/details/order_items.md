# Bảng `order_items`

> [← `orders`](orders.md) · [Mục lục](../database-design.md#2-phụ-lục-danh-sách-bảng-chính) · [`payments` →](payments.md)

**Mục đích:** Từng dòng trong đơn hàng: loại vé, số lượng, đơn giá tại thời điểm đặt.

| Field | Kiểu | Ràng buộc chính | Ý nghĩa |
|---|---|---|---|
| `id` | UUID | PK | Định danh dòng đơn |
| `order_id` | UUID | NOT NULL, FK → `orders(id)` ON DELETE CASCADE | Đơn hàng chứa dòng |
| `ticket_type_id` | UUID | NOT NULL, FK → `ticket_types(id)` | Loại vé được mua |
| `quantity` | INT | NOT NULL, CHECK `> 0` | Số lượng |
| `unit_price` | NUMERIC(12,2) | NOT NULL, CHECK `>= 0` | Đơn giá tại thời điểm đặt |
| `line_total` | NUMERIC(12,2) | NOT NULL, CHECK `= quantity * unit_price` | Thành tiền của dòng |
| `created_at` | TIMESTAMPTZ | NOT NULL | Thời điểm tạo |

**ENUM:** Không có.

**Quan hệ**

| Bảng liên quan | Loại | Mô tả |
|---|---|---|
| [`orders`](orders.md) | n–1 | Nhiều dòng thuộc một đơn |
| [`ticket_types`](ticket_types.md) | n–1 | Nhiều dòng tham chiếu một loại vé |
| [`tickets`](tickets.md) | 1–n | Mỗi dòng sinh ra `quantity` vé |

**Indexing**

| Index | Cột | Ghi chú |
|---|---|---|
| `uk_order_items_order_ticket_type` (unique) | `(order_id, ticket_type_id)` | Mỗi loại vé chỉ xuất hiện một dòng trong một đơn |
| `idx_order_items_order_id` | `order_id` | Các dòng của đơn |
| `idx_order_items_ticket_type_id` | `ticket_type_id` | Các dòng theo loại vé |

---

[← `orders`](orders.md) · [Mục lục](../database-design.md#2-phụ-lục-danh-sách-bảng-chính) · [`payments` →](payments.md)
