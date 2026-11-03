# Auction compact observed-world profile v1

Mục tiêu: state CSL chính → native OCL + original use4Goal outcome marking.
Đây là bộ authored requirements riêng, không tương đương normative/temporal
requirements của authored-v1. Không đổi bytes/semantics của bộ cũ để lấy PASS.

## Năm inputs trên main USE GUI

Plugins → JaCaMo / use4Goal verification...:

| Ô | File |
|---|---|
| CSL | compact-v1/auction.csl |
| JaCaMo project | D:/_CODE_BANK/Project_/08_Thesis/jacamo/examples/auction/auction.jcm |
| iStar | compact-v1/auction.istar |
| Mapping XML | compact-v1/auction-mapping.xml |
| OCL | compact-v1/auction.ocl |

Bỏ chọn Strict source checksums cho project được chọn. Prepare + Import →
MODEL_READY → Start Runtime. Runtime libs để mặc định checkout đã build hoặc
chọn classpath text của JaCaMo tương thích. Journal quota giữ 256 MiB.

## State và requirements

CSL chỉ giữ sáu giá trị Auction: id, running, task, best_bid, winner,
currentWinner. Field cuối không observable; neutral verified field provider
vẫn bắt buộc, không đoán từ winner hoặc log console. best_bid dùng finite
binary64 proof; không clamp Double.MAX_VALUE thành zero.

Original iStar engine kiểm AuctionFinished là AND của AuctionClosed và
WinnerPublished, theo đúng Artifact identity/incarnation; hai Auctions có scope
riêng. Đây là organizational observation viewpoint, không phải thêm Agent và
không credit Task cho một player khác. Các Goal này là requirements về outcome,
không là bản đổi tên của các Task A_start/P_bid trong model đầy đủ.

Native OCL kiểm giá không âm, published winner khớp field source và pre/post
start/bid/stop trên invocation/before/after thật. Các operation contracts hiện
trong bảng OCL / runtime binding checks; chúng không bị gọi là original iStar TaskMarking.
Full Jason per-invocation Task model vẫn chạy bằng authored-v1 khi cần.

Không thu full belief base, norm lifecycle, source clock hoặc exact MOISE
transition timestamps. XML quyết định source/capability/read-set; Java engine
không có tên Auction/goal/predicate làm rule. Cần kiểm normative requirements
thì chọn bộ đầy đủ và chấp nhận UNKNOWN nếu provider không có nguồn chính xác.

## Kết quả live và evidence đầy đủ

Bấm Start Runtime: cửa sổ Verification results mở riêng, input vẫn ở cửa sổ
ban đầu. Show results mở lại cửa sổ kết quả nếu đã ẩn. Main workflow có Go to State / Previous / Next / failure checkpoint;
có thể chọn summary milestones hoặc toàn bộ audit boundaries.

Goal & Task Status có một dòng cố định cho mỗi definition trong model iStar.
Chọn dòng để xem các occurrence/context/player/quality hiện tại. OCL và
operation checks nằm ở bảng riêng; chọn FAIL để xem expression và witness.
Summary là tổng hợp bảo thủ các observed instances, không chứng nhận một
population chưa quan sát. UNKNOWN không được chuyển thành false/PASS.

XML vẫn chọn id/running/task/winner làm projection ngắn. best_bid/currentWinner
và mọi bid vẫn được xử lý/verify. Journal/replay giữ mọi cut; summary/detail
state navigation có trong main Results và legacy Workbench.

Requirements-scoped status đứng riêng với Overall source coverage. KNOWN không
có nghĩa mọi nguồn JaCaMo đầy đủ. UNKNOWN/SKIPPED/ERROR không được biến thành
false/PASS. Last certified history là kết quả tại cut cũ, được tách khỏi current
qualified status và fence theo run/generation/context/player/episode.

Stop JaCaMo giữ USE sống; Restart chuẩn bị run mới, không carry history cũ.
Bộ authored-v1 và source-confirmed-v1 vẫn có thể chọn riêng khi cần yêu cầu cũ.

Goal condition `CONDITION_FALSE` chỉ là predicate chưa đạt tại cut đó. Original
iStar marking quyết định NONE/FULFILLED/VIOLATED; không gọi mọi điều kiện
achievement đang false là một vi phạm. Invariant hoặc operation contract false
vẫn là FAIL. Completion được đọc đúng cùng boundary/state hash/profile, không
dùng kết quả cũ để chứng nhận một cut mới hoặc gap.


Update state browser: main Results đã có lại Go to State / Previous / Next /
failure checkpoint, summary và all audit boundaries. Chọn cut cũ giữ state/OCL/
Goal rows đúng cut; footer hiển thị verdict và coverage phía dưới. Journal giữ
frozen accepted SOIL với hash gốc để đọc state sau RAM eviction. Original engine
history và current qualified evidence là hai cột riêng, không promote raw history.
Các câu trước nói navigation chỉ ở legacy Workbench được thay bằng update này.
