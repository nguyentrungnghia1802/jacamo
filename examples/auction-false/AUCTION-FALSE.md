# auction-false — lỗi cố ý khi đóng auction

Đây là bản sao của Auction hiện tại. Bản sao bỏ đúng cập nhật
`getObsProperty("running").updateValue(false)` khỏi `AuctionArtifact.stop()`.
Operation vẫn công bố currentWinner thật và có thể trả success, nhưng running
vẫn true. Đặc tả giữ nguyên: Closed và post của A_decide_t2 yêu cầu not running.

## Chạy qua USE

Mở `use-assembly/target/new/bin/start_use.bat`, chọn Plugins → JaCaMo → use4Goal
verification. Prepare + Import năm file cùng thư mục này:

1. Project: `auction.jcm`.
2. Mapping: `auction-mapping.xml` — profile JACAMO_DECLARATIVE_XML, AUTHORED.
3. CSL: `auction.csl`.
4. iStar: `auction.istar`.
5. OCL: `auction.ocl`.

Start Runtime. Các checkpoint hợp lệ tự tiếp tục; khi đến decide, kiểm tra native
POST/Task POST thất bại và giữ dừng. Xem exact invocation và before/after evidence.
Acknowledge & Continue cho tiếp tục đúng token được review; không sửa running,
không biến FAIL thành PASS và không xóa lịch sử. Không ép thứ tự hai execution
hoặc số checkpoint: công việc đã admit có thể cùng nằm trong một frozen cut.

Giữ tên MAS auction và tên các file/agents như bản gốc để mapping vẫn bind đúng.
Folder auction-false phân biệt biến thể. .csl/.istar/.ocl/ASL/MOISE không sửa.
XML chỉ repin hash Java source đã thay đổi; binary proof được tạo từ class thật.
Không dùng verdict/Goal label định trước: POST FAIL khác source operation FAILED.

USE tạo staging và compile source của project được chọn; không tái sử dụng build
cache của Auction gốc. Gradle source gốc được giữ nguyên; luồng được kiểm chứng
ở đây là owned runtime qua USE với pinned checkpoint runtime.

Kết quả và giới hạn kiểm chứng: `use-plugin/verification/auction-false/REPORT.md`
trong source checkout. Chưa có evidence thì không coi mô tả dự kiến là test PASS.
