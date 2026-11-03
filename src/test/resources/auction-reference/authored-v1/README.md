# Auction authored contract v3 — attempt versus accepted bid

`AUCTION_ATTEMPT_AND_ACCEPTED_BID/3.0` là thay đổi semantics được người dùng yêu cầu:
- `P_bid_t2` hoàn thành khi source step thành công hoặc thất bại đúng instruction `.fail` ở dòng 41, lý do callback `GOAL_LISTENER_FAILED:ia_failed`, cùng source hash, actor, scheme và invocation đã xác minh. Đây là nhánh mô phỏng không tham gia, không phải Artifact bid thành công.
- Lỗi lookup/focus, sai instruction, lỗi framework hoặc thiếu evidence không được chấp nhận thay thế.
- `O_bid` là AND của `O_bid_attempt` (ANY Participant attempt) và `O_bid_accepted` (native OCL `currentWinner <> 'no_winner'`). Source Artifact chỉ đổi trường này khi chấp nhận improving bid; lần bid đầu tiên phải cải thiện giá trị MAX ban đầu. Tất cả decline không thể đạt goal Auction. Non-improving bid thành công vẫn hoàn thành attempt.
- XML chọn `O_root` làm requirement kết quả; task/goal con vẫn hiển thị đầy đủ. Mọi invariant và operation contract authored vẫn bắt buộc. Execution failure hoặc predicate false không tự động là VIOLATION; invariant/operation contract false và original violated goal vẫn được giữ.
- CSL và OCL không đổi. OCL `WithinDeadline` vẫn tắt theo yêu cầu trước; không tuyên bố kiểm chứng deadline.
- iStar/XML success-only v2 giữ nguyên trong `references/`; strict OCL/XML và Draw.io cũ là reference của profile trước. Không gọi v3 là graph oracle cũ không đổi. `provenance.json` ghi hashes và expected difference.

Trong JaCaMo Workbench của USE, chọn:

1. Profile `JACAMO_DECLARATIVE_XML`, model input mode `AUTHORED`.
2. Mapping: `authored-v1/auction-mapping.xml`.
3. OCL: `authored-v1/auction_constraints_final_fixed.ocl`.
4. CSL: `authored-v1/auction_csl.csl`.
5. iStar: `authored-v1/auction_istar_final.istar`.
6. Đặt `journal quota (MiB)` là `256` trước import cho run Auction đầy đủ. Mặc định hiện tại là 256 MiB; khi hết quota, journal ghi GAP và kết quả không còn được coi là đầy đủ.
7. `Prepare + Import...`: chọn `auction.jcm` ở thư mục cha của `authored-v1`.
   Khi kết nối producer đã chạy bên ngoài, dùng `Import JaCaMo Project...`.
8. Sau khi import/capability gate cho phép, `Start Runtime` giải phóng reasoning.
   `Pause Jason`/`Resume Jason` giữ semantics ACK tại cycle boundary.
9. `Stop JaCaMo` chỉ dừng process do Workbench này sở hữu;
   `Stop monitor` chỉ dừng consumer. `Restart JaCaMo` chuẩn bị run mới và import lại;
   bấm `Start Runtime` để bắt đầu run đó.

Trong checkout đã build, classpath dùng
`jacamo-bridge-jacamo/target/jacamo-bridge-classpath.txt`. Trong distribution,
dùng `Runtime libs...` chọn file text chứa classpath các JAR framework JaCaMo
được cài trên máy; script dùng các Bridge JAR đi kèm distribution.
JDK 21 và Windows PowerShell cần có để chuẩn bị bản sao project. Project có
`gradlew.bat` được build trong staging; source gốc không bị chỉnh sửa.

CSL/iStar là canonical inputs; Java không sinh graph khác thay chúng.
Role `auction_capabilities` là definition-only abstraction; không tạo parent enactment.
Belief attributes là JSON chứa stored literals/rules của đúng player, không phải world truth.

**Giới hạn acceptance hiện tại:** xem `plan.md` và evidence `verification/authored-models`.
Static/native và component gates không thay cho live/package/main-GUI acceptance.
MOISE provider hiện không cung cấp exact internal transition timestamps;
`SchemeOrder`/`BidEvidence` không được gán timestamp publication để tạo PASS giả.
Thiếu source evidence phải hiển thị undefined/INCOMPLETE; không hiểu SKIPPED là PASS.

## Import project ở thư mục khác

Main workflow mặc định bỏ chọn **Strict source checksums** (trong Advanced
controls). Có thể chọn `.jcm` của project khác vị trí hoặc tên file; ASL,
MOISE, Java và resources được resolve theo project đã chọn và paths trong XML.
Source không phải giống byte của fixture. Selectors/assertions và official
source/model/binding checks vẫn bắt buộc; source thay đổi ngoài những điều kiện
XML mô tả không tự trở thành một verification requirement mới.

CSL/iStar/OCL vẫn phải đúng bộ requirements được mapping khai báo. Muốn đổi
requirements cần cập nhật specification tương ứng. Bật Strict source checksums
khi cần đối chiếu revision pin trong XML. API 5-argument cũ giữ strict;
API ImportSelection 6-argument cho phép chọn strictSourceChecksums=false.

Import thường ghi sourcePolicy=VALIDATED_SOURCE_V1 vào specification trong
memory, giữ reference hashes trong XML và lưu hash revision thực tế trong
source-admission-manifest. Không ghi lại source/XML trên đĩa. Replay kiểm tra
manifest revision đã frozen và checksum recording, không mở lại đường dẫn cũ.
Capability binary hashes/API/signatures vẫn được kiểm tra độc lập; provider
không hỗ trợ source/framework mới vẫn phải trả UNSUPPORTED/INCOMPLETE.


## Main UI — kết quả tách riêng

Sau Prepare + Import, bấm Start Runtime để mở cửa sổ Verification results
riêng. Show results mở lại view; đóng kết quả chỉ ẩn view, không stop producer.
Đóng cửa sổ input sẽ dispose các view và đóng phiên theo lifecycle hiện có.
Export .use/.cmd và Load OCL không còn trong main
five-input UI; state navigation đã được khôi phục như phần bên dưới; file OCL đã được chọn ở hàng input trước Prepare.

Bảng Goal & Task giữ một dòng theo definition iStar, không thêm dòng cho mỗi
callback/Artifact/obligation. Chọn dòng để xem chi tiết từng occurrence/player.
Bảng OCL riêng vẫn có thể thay đổi theo checks hiện tại. Chọn FAIL để xem đúng
context object, invocation, expression và witness; không dựa vào log console.

Log Winner hoặc I Won chỉ xác nhận Artifact outcome, không chứng minh mỗi
participant đã hoàn thành P_bid_t2. Current authored profile giữ user test override:
WithinDeadline đang comment, mapping thiết yếu không project AuctionObligation.
Muốn kiểm deadline, dùng full-observers reference và phục hồi clause/binding/hash
như hướng dẫn cuối file. ConfirmedSchemeOrder/ObservedBidEvidence là active requirements của v2;
thiếu qualified source evidence vẫn SKIPPED/INCOMPLETE. Không sửa OCL body hoặc source
để ép PASS. Consumer có thể còn xử lý backlog sau log winner; kiểm accepted cut,
source/verification timestamps và witness, không coi winner là end-of-stream.


## State browser được khôi phục

Trong cửa sổ kết quả dùng Go to State, Previous/Next, Go to Failure Checkpoint;
Show all audit boundaries mở toàn bộ cut, mặc định vẫn có summary milestones.
Chọn state cũ sẽ giữ nguyên state/result tại cut đó khi runtime tiếp tục chạy.
Hai ô dưới cùng hiển thị VIOLATION / INCOMPLETE / PENDING / COMPLETE_OBSERVED
và coverage/freshness. COMPLETE_OBSERVED chỉ là observed scope, không proof cho
mọi JaCaMo internals.

Bảng hiện 12 actor Goal/Task definitions của model. Ba social dependums D_start,
D_bid, D_decide vẫn trong diagram/dependency semantics, nhưng original marking
không tạo runtime occurrence riêng nên không thêm ba hàng UNKNOWN giả.
Original history (raw) lấy aggregate từ original engine; MIXED nghĩa observed
instances khác trạng thái, không áp thêm AND/ANY. Certified execution giữ certificate Task/ACHIEVE đúng episode khi marker đã kết
thúc. Không lấy raw FULFILLED làm proof. Chọn dòng để xem player/context, certificate
và current quality. Internal ANY/ALL receivers không phải thêm instances của source
Goal. Source Goal O_bid chỉ cần một Participant thực sự hoàn thành trong mỗi scheme;
bảng P_bid vẫn phản ánh riêng từng participant, không credit agent chưa bid.

Từ bản này frozen SOIL của accepted cuts có trong journal hash chain hiện có;
viewer không khôi phục bằng cách đọc state live hay tạo MSystem thứ hai. Recording
cũ thiếu ảnh của state đã rời RAM vẫn ghi rõ unavailable, không tự dựng ảnh giả.


## Essential business states

Mapping XML dùng `<presentation milestones="DOMAIN">`: chỉ vòng đời AuctionOrg,
auctionGroup, auctioneer/participant occurrences và Auction.id/running/task/best_bid/
winner/currentWinner tạo business-state milestones. Mọi native state, step, OCL,
norm/observer boundary vẫn ở full audit/replay; dùng Show all audit boundaries hoặc
Inspect milestone interval để kiểm tra. Không có giới hạn/truncation 30 state.
Milestone count phụ thuộc các thay đổi thật của source, không phải số dòng console.
Diagnostic-only boundaries (quality/OCL/step) không tạo thêm business state.
Go to Failure Checkpoint vẫn mở đúng audit cut đầu tiên có lỗi, kể cả khi cut đó
không nằm trong domain summary. Không bỏ failure/gap khỏi journal/replay.

Active native CSL/iStar/OCL requirements không đọc optional beliefBase/belief_*
attributes. Do đó profile này bỏ beliefViews/beliefBindings khỏi runtime plan:
producer không gửi cycles/storedBeliefObservation liên tục. Các optional attributes
vẫn nguyên trong authored CSL nhưng undefined (không giả []/false). Khi thêm
requirement đọc chúng, phải khai báo lại source/binding tương ứng. Step, operation,
role/player/incarnation, scheme, NPL/clock requirements vẫn giữ nguyên.

Footer có Last completion delay; tooltip có observed/accepted/verified timestamps.
Độ trễ không tự chứng minh mất dữ liệu, cũng không chứng minh đã consume hết source.
Watermark/admission/gap và exact native before/after vẫn là authority. Console đã
in winner không phải một barrier xác nhận USE đã verify hết các observations.


Active OCL hiện comment WithinDeadline; native AST không đọc AuctionObligation
hoặc AuctionMonitor.now. Essential mapping không materialize các observer này,
nhưng vẫn giữ raw NPL/source-clock capabilities và journal evidence. Schema lớp
AuctionObligation cũng không đưa vào MModel để việc bật lại OCL mà quên source
projection sẽ fail compile, không PASS vacuously trên một population rỗng.
Muốn verify deadline: dùng `references/auction-mapping-full-observers.xml`, phục hồi
clause WithinDeadline trong OCL và binding trong XML, cập nhật authored OCL hash.
Reference này dùng cùng CSL/iStar; không fallback tự động sang nó.

## Selected requirements / observer contract v2

Main verdict COMPLETE_OBSERVED chỉ chứng minh các requirement được chọn trên
accepted observed prefix: Goal/Task có certificate, native OCL có kết quả và
freshness CURRENT_OBSERVED. Overall source coverage vẫn hiển thị riêng và có thể
PARTIAL/INCOMPLETE. Missing/undefined/error/unsupported, gap chưa phục hồi hoặc
state stale không được đổi thành PASS; legacy profiles giữ policy cũ.

Resource là supporting input cho đúng Task occurrence, không có Goal/Task marking
riêng. Resource availability vẫn qua EngineInputs/needed-by gate của original
engine. Không còn đếm A_artifact/P_artifact thành hidden UNKNOWN requirements.
Invariant body vẫn hiện đầy đủ, nhưng số unavailable đếm aggregate một lần.

`ObservedBidEvidence` dùng bidSatisfiedObserved từ qualified complete public state
history thay vì timestamp chỉ để kiểm SATISFIED. `ConfirmedSchemeOrder` dùng
first source publication confirmations được provider MOISE_PUBLIC_CONFIRMATION/1.0
xác nhận. Đây KHÔNG phải exact internal MOISE truth-transition order; không lấy
thời gian TCP/poll/operation return để giả timestamp. Clock/prefix không đủ vẫn
undefined/SKIPPED. Original exact-time contract vẫn ở:
- Mapping: references/auction-mapping-strict-v1.xml
- OCL: references/auction_constraints_strict_v1.ocl
Chọn cả hai reference này cùng CSL/iStar hiện tại để chạy strict; exact time còn
UNSUPPORTED nên INCOMPLETE là kết quả đúng. Full-observers reference cũng thuộc
strict contract cũ; không trộn nó với OCL v2. Deadline override vẫn giữ disabled.
Với v2, nếu cả Goal/Task và các native requirement có đủ evidence, kết quả là
COMPLETE_OBSERVED dù overall source coverage vẫn INCOMPLETE. Nếu một participant
thất bại/missing, invariant false hoặc nguồn thiếu, verdict vẫn phản ánh đúng.

Đóng JVM USE cũ và reimport cả mapping/OCL mới sau khi đổi package. Không chạy
replay cũ bằng binary/program khác hash và không sửa receipt lịch sử.

## Terminal failures và milestone bootstrap (2026-10-29)

Một Task Jason FAILED có exact PRE/POST đã admission được giữ trên ledger hiện có,
đúng context/player/role interval/episode/invocation, kể cả marker đã rời current cut.
Certified execution hiện FAILED; marking gốc vẫn PENDING vì TaskMarking gốc không
có trạng thái FAILED/VIOLATED. Parent được tính theo original propagation; không
đổi mọi parent thành FAILED, không làm hỏng ANY đã có nhánh thành công. Native
runtime result giữ EXECUTION_OUTCOME FAIL cùng certificate để UI và evidence nhất
quán. Thiếu/partial/error hoặc chỉ có post=false không tự tạo failure certificate.
Retry/new episode/run không được mượn outcome cũ. Click dòng để đọc invocation/cut.

DOMAIN summary bỏ pending/unqualified cuts và empty bootstrap trước khi có đối tượng
CSL được chọn. Một population trở lại rỗng sau khi từng có đối tượng vẫn là state
thật và được giữ. Creation/removal/role population/attribute changes do XML chọn
vẫn cần để kiểm model, không bị loại chỉ vì SOIL chứa !new/!insert. Default preview
hiện selected CSL state, audit details mới hiện native state/SOIL. Mọi audit cut và
failure checkpoint còn nguyên; không lọc bằng tên Auction hoặc chuỗi câu lệnh.
