package org.phoneai.teamwork.capability

/** Engineering evidence, not runtime authorization. No user/model switch can promote this ledger. */
enum class EvidenceLevel(val label: String) {
    OFFLINE_ONLY("仅离线逻辑验证"), LIMITED_DEVICE("有限实机证据"), UNKNOWN("待能力验证"), OBSERVED_FAILURE("已有失败样本")
}
data class Capability(val id: String, val owner: String, val label: String, val level: EvidenceLevel,
    val scope: String, val acceptance: String)
data class DeferredFeature(val id: String, val label: String, val dependencies: Set<String>)

object CapabilityCatalog {
    val capabilities = listOf(
        Capability("WF-01", "Workflow", "步骤与正文来源", EvidenceLevel.OFFLINE_ONLY,
            "确定性模板、冻结来源；不代表外部 App 收到正文", "七步来源与实际送达正文分别核验"),
        Capability("EXEC-01", "Phone Tools", "固定公开题暂存", EvidenceLevel.LIMITED_DEVICE,
            "DeepSeek 2.5.2 (273)，人工展开空白输入框；一次 116 字写入并精确回读", "扩大页面样本；其他版本和 App 分别验收"),
        Capability("MODEL-02", "本地小模型", "输入框定位", EvidenceLevel.LIMITED_DEVICE,
            "Qwen3-VL-2B，DeepSeek 正例及 Kimi 语音模式暂停；无统计成功率", "标注正负样本，分别报告错选、过度暂停、超时"),
        Capability("NAV-01", "Phone Tools / App 适配", "自主进入目标会话", EvidenceLevel.UNKNOWN,
            "打开入口与展开输入框目前由人完成", "各 App 的进入、返回、弹窗及错误页；不得误入历史会话"),
        Capability("SEND-01", "Phone Tools", "发送与送达证据", EvidenceLevel.UNKNOWN,
            "尚未自动发送；按钮观察不等于已送达", "精确正文出现在当前会话的 user 消息位置；回调成功不算送达"),
        Capability("THREAD-01", "App 适配核验函数", "既有会话身份", EvidenceLevel.UNKNOWN,
            "仅有特定空白窗口绑定，无历史会话恢复", "同名会话、窗口变化、重进 App 时用正文锚点拒绝错会话"),
        Capability("CAPTURE-01", "App 适配核验函数", "回答角色与完成信号", EvidenceLevel.UNKNOWN,
            "没有完整回答提取实测", "区分 user/assistant、生成暂停、失败提示；有完成证据才可转发"),
        Capability("LONG-01", "Phone Tools / App 适配", "长回答完整取回", EvidenceLevel.UNKNOWN,
            "未知虚拟列表、滚动、折叠和截断行为", "跨屏回答与独立原文逐字比对，缺片段不补写"),
        Capability("APPS-01", "Phone Tools / App 适配", "Kimi 与豆包交互", EvidenceLevel.UNKNOWN,
            "已有入口观察；无写入、发送、回答取回验证", "两款分别验证版本、输入、提交、会话和完整回答"),
        Capability("RUNTIME-01", "本地 backend / Android 生命周期", "后台持续执行", EvidenceLevel.OBSERVED_FAILURE,
            "一次后台推理被 LOW_MEMORY 终止；按需加载 projector 后一次暂存成功", "多次冷启动、切 App、内存压力和取消；记录分母、时延与退出原因"),
        Capability("STATE-01", "State & Recovery", "真实副作用中断恢复", EvidenceLevel.OFFLINE_ONLY,
            "纯状态转换可回放；Room 与发送边界未实机中断验证", "意图落盘前后、点击后未确认时杀进程；不自动重发"),
        Capability("AI-01", "外部 AI App", "业务回答与审阅质量", EvidenceLevel.UNKNOWN,
            "没有业务质量评测；收到了回答不等于正确", "独立依据核验事实、审阅贡献、综合保真与来源"),
        Capability("MODEL-04", "本地小模型", "输入框之外的控件判断", EvidenceLevel.UNKNOWN,
            "目前只给定位输入框 / 暂停目标；未验证通用导航或发送定位", "独立标注目标集与负例，不能套用输入框定位结果")
    )
    val deferred = listOf(
        DeferredFeature("AUTO_ENTER", "自动打开并进入会话", setOf("NAV-01", "THREAD-01", "MODEL-04")),
        DeferredFeature("SINGLE_EXCHANGE", "自动发送并保存一次完整回答", setOf("SEND-01", "CAPTURE-01", "THREAD-01")),
        DeferredFeature("FORWARD_LONG", "长回答自动转交其他参与者", setOf("CAPTURE-01", "LONG-01", "SEND-01")),
        DeferredFeature("RESUME", "进程退出后自动续跑", setOf("STATE-01", "THREAD-01", "RUNTIME-01")),
        DeferredFeature("THREE_APP", "三 App 七步自动协作", setOf("NAV-01", "MODEL-04", "SEND-01", "THREAD-01", "CAPTURE-01", "LONG-01", "APPS-01", "RUNTIME-01", "STATE-01")),
        DeferredFeature("QUALITY", "宣称协作提高回答质量", setOf("AI-01"))
    )
    init {
        require(capabilities.map { it.id }.distinct().size == capabilities.size)
        require(deferred.all { f -> f.dependencies.isNotEmpty() && f.dependencies.all { id -> capabilities.any { it.id == id } } })
    }
    fun blockers(featureId: String): List<Capability> {
        val feature = requireNotNull(deferred.singleOrNull { it.id == featureId }) // Unknown features fail closed.
        return capabilities.filter { it.id in feature.dependencies }
    }
    // Deferred capabilities cannot be inferred from unit tests or a different App's successful sample.
    fun blockedReason(featureId: String): String = "CAPABILITY_PENDING:" + blockers(featureId).joinToString(",") { it.id }
}
