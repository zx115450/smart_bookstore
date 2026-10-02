package com.zx.ai.faq;

import java.util.ArrayList;
import java.util.List;

/**
 * FAQ 静态知识库：集中维护业务规则条目。
 * <p>
 * 单一数据源，避免 {@link com.zx.ai.tool.FaqTool} 关键词匹配与
 * {@link FaqEmbeddingIndexer} 向量索引出现两份不一致的文案。
 * 新增/修改 FAQ 只需在此处编辑，重建索引后即生效。
 */
public final class FaqKnowledgeBase {

    private FaqKnowledgeBase() {
    }

    /**
     * 返回只读的 FAQ 条目列表，索引顺序稳定（用于向量文档 id 编号）。
     */
    public static List<FaqEntry> entries() {
        List<FaqEntry> kb = new ArrayList<>();

        kb.add(FaqEntry.of(
                "怎么借书",
                """
                借书流程（需登录 USER 角色）：
                1. 在书目页或 AI 客服找到想借的书，记下 bookId 与架位 shelfLocation。
                2. 调用 POST /api/borrow/orders，body: { "bookId": <id> }，提交借阅申请，订单状态变为 APPLIED（待取书）。
                3. 到馆后向管理员确认取书，订单转为 BORROWED，借阅库存 -1，系统按 book.borrowDays（默认 30 天）计算 due_at 到期时间。
                注意：每位用户同时只能有一笔未归还的借阅（APPLIED 或 BORROWED 或 OVERDUE），重复借阅会被拒绝。""",
                "借书", "借阅", "怎么借", "申请借阅", "借书流程", "borrow", "APPLIED", "待取书"
        ));

        kb.add(FaqEntry.of(
                "怎么还书",
                """
                还书流程：
                1. 进入「我的借阅」POST /api/borrow/orders/mine 查找状态为 BORROWED 或 OVERDUE 的订单。
                2. 调用 POST /api/borrow/orders/{id}/return 归还，订单状态变为 RETURNED。
                归还后借阅额度释放，可继续借下一本。""",
                "还书", "归还", "怎么还", "return", "RETURNED", "归还流程"
        ));

        kb.add(FaqEntry.of(
                "待取书与架位",
                """
                待取书（APPLIED 状态）查看架位：
                1. 调用 GET /api/borrow/orders/mine?status=APPLIED 查看待取书列表。
                2. 订单响应中包含 shelfLocation（如「2楼 A-02 第3层」），按此架位到馆取书。
                3. 取书后请管理员确认，订单转 BORROWED 后开始计算到期时间。""",
                "待取书", "取书", "架位", "在哪取", "怎么取", "shelfLocation", "APPLIED"
        ));

        kb.add(FaqEntry.of(
                "借阅到期与逾期",
                """
                借阅到期规则：
                - 借阅期限由 book.borrowDays 决定，默认 30 天，自管理员确认取书（APPLIED → BORROWED）起算。
                - 系统通过 Redis ZSET + Lua 定时扫描到期订单，到期后状态自动从 BORROWED 转为 OVERDUE（逾期）。
                - 逾期后仍可归还（OVERDUE → RETURNED），但请尽快归还。
                - 线上阅读与配套视频：仅 BORROWED（借阅中且未逾期）可解锁付费章与完整播放；一旦变成 OVERDUE，权限立刻降级为试看（与未借相同）。归还（RETURNED）后同样只剩试看。已购买（购书订单 PAID）不受借阅逾期影响，可永久阅读全文。
                - 个人借阅状态请查 GET /api/borrow/orders/mine。""",
                "到期", "逾期", "超期", "overdue", "due", "到期时间", "借阅期限", "borrowDays", "BORROWED", "OVERDUE",
                "停权", "降级试看", "看不了", "电子书锁了"
        ));

        kb.add(FaqEntry.of(
                "线上书试看与解锁",
                """
                线上电子书试看与解锁规则：
                - 实体书可绑定线上书（ebook）。目录接口 GET /api/reader/ebooks/{ebookId}/chapters；读章 GET /api/reader/ebooks/{ebookId}/chapters/{chapterNo}。
                - 试看：前 N 章（ebook.preview_chapters，默认可为 2）标记为免费，未借未购也可读。
                - 付费章无权限时返回业务码 5103（试看拒绝），服务端不会向媒资拉取正文。
                - 解锁全文条件（同一 bookId）：① 借阅状态为 BORROWED（未逾期）；或 ② 购书订单已支付 PAID。待取书 APPLIED、逾期 OVERDUE、已归还 RETURNED 均不能解锁付费章。
                - 配套视频：GET /api/books/{bookId}/media/{refId}/play；无完整权时为试看（带 previewSeconds），有完整权返回完整播放地址。
                - 查某书是否有电子书：先 searchBooks / getBookDetail，看返回字段 hasEbook、ebookId、previewChapters。""",
                "电子书", "线上书", "试看", "解锁", "付费章", "5103", "ebook", "ebookId", "preview",
                "第三章打不开", "为什么锁了", "完整阅读", "配套视频", "阅读权限"
        ));

        kb.add(FaqEntry.of(
                "学习助手与阅读笔记",
                """
                学习助手（Study Agent）与笔记：
                - 学习助手入口 POST /api/reader/agent/chat（需登录），与客服 /api/ai/chat 相互独立，不共用人设。
                - 能力：总结章节、改写/合并笔记并另存；读取章节正文前会做与读章相同的试看鉴权，锁定章不会编造正文。
                - 笔记 CRUD：划线/手动笔记走 /api/reader/notes；总结结果 source_type 可为 AI_SUMMARY / AI_REWRITE / AI_MERGE。
                - 客服本人不代写章节总结；若用户要总结某章，应引导其打开阅读页的学习助手，并说明需对该章有阅读权限。""",
                "学习助手", "Study Agent", "总结章节", "改写笔记", "合并笔记", "阅读笔记", "划线",
                "AI总结", "笔记", "agent"
        ));

        kb.add(FaqEntry.of(
                "连续签到奖励",
                """
                签到与连续签到奖励：
                - 签到需登录 USER 角色，且需在预约的自习室场馆内、签到窗口时间（开课前 15 分钟 ~ 开课后 15 分钟）内进行。
                - 调用 GET /api/checkin/eligible 获取可签到订单与当日场馆码，POST /api/checkin 提交签到。
                - 连续签到 streak 达到 7 天（checkin.streakRewardDays=7）自动发放 CHECKIN_7 优惠券一张，发放后连续签到计数清零重新累计。
                - 签到日历与连续天数见 GET /api/checkin/calendar、GET /api/checkin/streak。""",
                "签到", "打卡", "连续签到", "streak", "签到奖励", "7天", "七天", "送券", "CHECKIN_7", "优惠券", "checkin", "venueCode", "场馆码"
        ));

        kb.add(FaqEntry.of(
                "怎么预约自习室",
                """
                自习室预约流程（需登录 USER 角色）：
                1. GET /api/reservation/resources 浏览可预约场馆。
                2. GET /api/reservation/resources/{id}/slots?date=YYYY-MM-DD 查看某日时段。
                3. GET /api/reservation/slots/{slotId}/seats 查看该时段可用座位。
                4. POST /api/reservation/orders 提交预约订单，body 含 slotId、seatId 等。
                5. 「我的预约」GET /api/reservation/orders/mine；如需取消 POST /api/reservation/orders/{id}/cancel。
                预约成功后请按时到馆签到。""",
                "预约", "自习室", "座位", "怎么预约", "reservation", "时段", "slot", "seat", "场馆"
        ));

        kb.add(FaqEntry.of(
                "购书下单与自动取消",
                """
                购书下单流程（需登录 USER 角色）：
                1. POST /api/trade/orders 创建购书订单（含书目与数量），状态为 PENDING_PAY（待支付）。
                2. POST /api/trade/orders/{id}/pay 支付，支付成功转 PAID。
                3. 未支付订单会在 15 分钟（bookstore.trade.unpaidCancelDelayMinutes=15）后由 RabbitMQ 延迟队列自动取消，释放库存。
                4. 也可主动 POST /api/trade/orders/{id}/cancel 取消。「我的订单」GET /api/trade/orders/mine。""",
                "购书", "买书", "下单", "支付", "订单", "自动取消", "取消订单", "trade", "PENDING_PAY", "15分钟", "超时取消", "未支付"
        ));

        kb.add(FaqEntry.of(
                "优惠券怎么用",
                """
                优惠券使用规则：
                - 优惠券来源：连续签到 7 天赠送 CHECKIN_7 券；秒杀活动发放秒杀券；管理员后台发放。
                - 下单（购书）时可在创建订单请求中选用符合条件的可用券，系统按券类型（满减 FIXED / 百分比 PERCENT）和 thresholdAmount 计算折扣。
                - 优惠券使用后状态由 UNUSED 转为 USED，不可重复使用。
                - 查看我的优惠券与可用券请走 coupon 相关接口。""",
                "优惠券", "券", "coupon", "CHECKIN_7", "秒杀", "用券", "满减", "折扣", "USED", "UNUSED"
        ));

        return List.copyOf(kb);
    }
}
