#### Cấu hình RabbitMQ
- Convertor -> nếu dùng message dạng JSON -> dùng Jackson2JsonMessageConverter
- RabbitTemplate -> class chứa method `convertAndSend()` dùng để gửi Message vào Broker
- Mỗi Module cần Broker:
    - Config -> cấu hình Exchange, Queue, Binding -> lưu ý `durable` và `autoDelete`
    - Producer -> cài đặt hàm `send` đúng Exchange, Routing Key
    - Consumer -> cài đặt hàm `recieve` đúng Queue