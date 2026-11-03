# Auction — JaCaMo → USE/use4Goal

## Bộ input hiện hành

Mở USE (Java 21), chọn **Plugins → JaCaMo → use4Goal verification**.
Chọn đủ năm inputs:

1. CSL: `authored-v1/auction_csl.csl`.
2. JaCaMo project: `auction.jcm` trong thư mục này, hoặc project Auction tương thích ở vị trí khác (ví dụ repo JaCaMo `examples/auction/auction.jcm`).
3. iStar: `authored-v1/auction_istar_final.istar`.
4. Mapping: `authored-v1/auction-mapping.xml`.
5. OCL: `authored-v1/auction_constraints_final_fixed.ocl`.

Bấm **Prepare + Import**, rồi **Start Runtime**. Kết quả ở cửa sổ riêng:
Execution & Results / CSL model / iStar model; thông tin kỹ thuật ở Advanced / Diagnostics.
JaCaMo chạy process riêng qua TCP, USE có một authoritative MSystem.
Runtime libs tự phát hiện trong checkout đã build; nếu cần chọn
`jacamo-bridge-jacamo/target/jacamo-bridge-classpath.txt`.

## Semantics hiện hành

Contract `AUCTION_ATTEMPT_AND_ACCEPTED_BID/3.0`: Participant hoàn thành lượt
tham gia theo source callback và XML, gồm nhánh `.fail` mô phỏng được khai báo
chính xác. Goal Auction vẫn cần ít nhất một bid thật được Artifact chấp nhận.
Lỗi khác không tự được chấp nhận; missing/unsupported không thành PASS.
`COMPLETE_OBSERVED` chỉ nói về requirements đang bật trên observed execution;
không đồng nghĩa quan sát toàn bộ framework. WithinDeadline vẫn tắt theo yêu
cầu thử nghiệm trước; xem `authored-v1/provenance.json`.

## Các file khác được giữ có chủ đích

- `src/`, `.jcm`, `build.gradle`: source/runtime project.
- `authored-v1/references/`: model/contract trước migration, dùng đối chiếu và regression; không chọn nhầm với bộ hiện hành.
- `compact-v1/`, `source-confirmed-v1/`: profile riêng có test/package tham chiếu, không phải input mặc định.
- Các model/XML/Draw.io ở root: oracle và generated-profile regression.
- `case-manifest.json`, `provenance/`: nguồn gốc, hash, license và source parity fixtures; không phải engine Java thứ hai.

XML `auction_jacamo_to_csl_rules.xml` của profile Java Auction đã retire được
loại bỏ; không còn hướng dẫn chạy `AUCTION_OBSERVED_V1`.
Trong ZIP, case này ở `examples/auction-semantic`.

Journal quota mặc định 256 MiB (`268435456` bytes); launcher hỗ trợ
`-RuntimeJournalMaxBytes` nếu cần chọn quota riêng. Hết quota phải báo GAP,
không được xoá history để tạo COMPLETE. **Original iStar history** nằm trong
Advanced / Diagnostics → Verification Details; overall source coverage có thể
là **INCOMPLETE** dù requirements được quan sát đã hoàn thành.
