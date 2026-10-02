package com.zx.common.exception;

/**
 * 全局错误码定义。
 * <p>
 * 按业务域划分区间，避免模块间重复或冲突：
 * <ul>
 *   <li>0     — 成功</li>
 *   <li>1000  — 通用参数错误</li>
 *   <li>1001~1099 — 认证</li>
 *   <li>2001~2099 — 授权 / 会话 / JWT</li>
 *   <li>3001~3099 — 预约</li>
 *   <li>3100~3199 — 签到</li>
 *   <li>4001~4099 — 书城公共（图书 / 分类 / 书架）</li>
 *   <li>4100~4199 — 借阅</li>
 *   <li>4200~4299 — 购物车</li>
 *   <li>4300~4399 — 优惠券</li>
 *   <li>4400~4499 — 购书交易</li>
 *   <li>4500~4599 — 秒杀</li>
 *   <li>5001~5099 — AI 客服</li>
 *   <li>5101~5199 — 线上阅读</li>
 *   <li>5201~5299 — 阅读笔记</li>
 *   <li>5301~5399 — Study Agent</li>
 *   <li>6001~6099 — 媒资依赖</li>
 *   <li>9999  — 未知系统错误</li>
 * </ul>
 */
public final class ErrorCode {

    private ErrorCode() {}

    public static final int SUCCESS = 0;
    public static final int UNKNOWN_ERROR = 9999;

    // 通用参数错误
    public static final int BAD_REQUEST = 1000;

    // 认证：1001~1099
    public static final int AUTH_INVALID_CREDENTIALS = 1001;
    public static final int AUTH_INVALID_PARAM = 1002;
    public static final int AUTH_SEND_CODE_LIMITED = 1003;
    public static final int AUTH_LOGIN_LOCKED = 1004;
    public static final int AUTH_OAUTH_CONFIG_ERROR = 1005;

    // 授权 / 会话 / JWT：2001~2099
    public static final int TOKEN_EXPIRED = 2001;
    public static final int TOKEN_INVALID = 2002;
    public static final int TOKEN_MISSING = 2003;
    public static final int SESSION_REVOKED = 2004;
    public static final int USER_DISABLED = 2005;
    public static final int TOKEN_REVOKED = 2006;
    public static final int ACCESS_DENIED = 2007;

    // 预约：3001~3099
    public static final int RESERVATION_SLOT_NOT_FOUND = 3001;
    public static final int RESERVATION_NO_CAPACITY = 3002;
    public static final int RESERVATION_DUPLICATE_BOOKING = 3003;
    public static final int RESERVATION_INVALID_STATUS = 3004;
    public static final int RESERVATION_FORBIDDEN = 3005;
    public static final int RESERVATION_DAILY_LIMIT_EXCEEDED = 3006;
    public static final int RESERVATION_RESOURCE_NOT_FOUND = 3007;
    public static final int RESERVATION_ORDER_NOT_FOUND = 3008;
    public static final int RESERVATION_SLOT_EXPIRED = 3009;
    public static final int RESERVATION_SEAT_NOT_FOUND = 3010;
    public static final int RESERVATION_SEAT_ALREADY_BOOKED = 3011;

    // 签到：3100~3199
    public static final int CHECKIN_ALREADY_CHECKED_IN_TODAY = 3100;
    public static final int CHECKIN_NO_ELIGIBLE_RESERVATION = 3101;
    public static final int CHECKIN_DATE_MISMATCH = 3102;
    public static final int CHECKIN_ORDER_ALREADY_CHECKED_IN = 3103;
    public static final int CHECKIN_OUTSIDE_WINDOW = 3104;
    public static final int CHECKIN_ORDER_NOT_FOUND = 3105;
    public static final int CHECKIN_INVALID_VENUE_CODE = 3106;
    public static final int CHECKIN_VENUE_MISMATCH = 3107;
    public static final int CHECKIN_FORBIDDEN = 3108;

    // 书城公共：4001~4099
    public static final int BOOKSTORE_BOOK_NOT_FOUND = 4001;
    public static final int BOOKSTORE_OUT_OF_STOCK = 4002;
    public static final int BOOKSTORE_HAS_UNRETURNED = 4003;
    public static final int BOOKSTORE_INVALID_STATUS = 4004;
    public static final int BOOKSTORE_CATEGORY_NOT_FOUND = 4005;
    public static final int BOOKSTORE_BOOKSHELF_NOT_FOUND = 4006;
    public static final int BOOKSTORE_ORDER_NOT_FOUND = 4007;
    public static final int BOOKSTORE_FORBIDDEN = 4008;

    // 借阅：4100~4199
    public static final int BORROW_OUT_OF_STOCK = 4100;
    public static final int BORROW_HAS_UNRETURNED = 4101;
    public static final int BORROW_INVALID_STATUS = 4102;
    public static final int BORROW_ORDER_NOT_FOUND = 4103;
    public static final int BORROW_FORBIDDEN = 4104;

    // 购物车：4200~4299
    public static final int CART_ITEM_NOT_FOUND = 4200;
    public static final int CART_FORBIDDEN = 4201;
    public static final int CART_BOOK_NOT_FOUND = 4202;
    public static final int CART_OUT_OF_STOCK = 4203;
    public static final int CART_EMPTY = 4204;

    // 优惠券：4300~4399
    public static final int COUPON_NOT_FOUND = 4300;
    public static final int COUPON_NOT_USABLE = 4301;
    public static final int COUPON_THRESHOLD_NOT_MET = 4302;
    public static final int COUPON_TEMPLATE_NOT_FOUND = 4303;
    public static final int COUPON_UNSUPPORTED_TYPE = 4304;
    public static final int COUPON_EXHAUSTED = 4305;

    // 购书交易：4400~4499
    public static final int TRADE_ORDER_NOT_FOUND = 4400;
    public static final int TRADE_INVALID_STATUS = 4401;
    public static final int TRADE_INSUFFICIENT_BALANCE = 4402;
    public static final int TRADE_OUT_OF_STOCK = 4403;
    public static final int TRADE_FORBIDDEN = 4404;
    public static final int TRADE_BOOK_NOT_FOUND = 4405;

    // 秒杀：4500~4599
    public static final int SECKILL_ALREADY_PARTICIPATED = 4500;
    public static final int SECKILL_SOLD_OUT = 4501;
    public static final int SECKILL_NOT_IN_WINDOW = 4502;
    public static final int SECKILL_ACTIVITY_NOT_FOUND = 4503;
    public static final int SECKILL_PROCESSING = 4504;
    public static final int SECKILL_CONFIG_ERROR = 4505;
    public static final int SECKILL_SYSTEM_BUSY = 4506;
    public static final int SECKILL_NOT_PARTICIPATED = 4507;
    /** 秒杀入口令牌桶限流触发 */
    public static final int SECKILL_RATE_LIMITED = 4508;

    // AI 客服：5001~5099
    public static final int AI_SERVICE_UNAVAILABLE = 5001;
    public static final int AI_RATE_LIMITED = 5002;
    public static final int AI_SESSION_EXPIRED = 5003;
    public static final int AI_BAD_REQUEST = 5004;

    // 线上阅读：5101~5199
    /** 电子书不存在或已下架 */
    public static final int READER_EBOOK_NOT_FOUND = 5101;
    /** 试看拒绝（未解锁章，禁止调媒资拉正文） */
    public static final int READER_PREVIEW_DENIED = 5103;

    // 阅读笔记：5201~5299
    public static final int READER_NOTE_FORBIDDEN = 5201;
    /** 锁定章划线过长（防泄文） */
    public static final int READER_NOTE_QUOTE_DENIED = 5202;

    // Study Agent：5301~5399
    public static final int READER_AGENT_RATE_LIMITED = 5301;

    // 媒资依赖：6001~6099
    /** 图书媒资绑定不存在 */
    public static final int MEDIA_REF_NOT_FOUND = 6001;
    /** 媒资不可用（超时 / 5xx / Token 失败 / 未启用） */
    public static final int MEDIA_UNAVAILABLE = 6002;
    /** 章对象不存在或未切完；或 VIDEO fileId 不存在 / 未转码完成 */
    public static final int MEDIA_CHAPTER_NOT_READY = 6003;
    /** 无完整播放权限且不允许试看（preview_seconds=0） */
    public static final int MEDIA_PLAY_DENIED = 6004;
    /** 播放签名申请失败 */
    public static final int MEDIA_PLAY_SIGN_FAILED = 6005;
}
