package com.xjb.linti.data.sample

import com.xjb.linti.domain.Question
import com.xjb.linti.domain.QuestionOption
import com.xjb.linti.domain.QuestionType

object SampleContent {
    const val BANK_NAME = "示例题库 · 临床基础"
    const val BANK_NOTE = "首次安装的虚构教材题示例，可放心编辑或删除。"
    const val SOURCE_FILE_NAME = "示例题库-临床基础.txt"

    fun questions(): List<Question> = listOf(
        Question(
            sourceNumber = "1",
            type = QuestionType.SINGLE,
            stem = "关于细胞膜物质转运的教材叙述，正确的是？",
            options = options(
                "单纯扩散不需要载体",
                "所有离子均通过主动转运",
                "蛋白质主要通过自由扩散跨膜",
                "水的跨膜转运只依赖泵",
            ),
            answer = "A",
            explanation = "单纯扩散依赖浓度梯度，不需要载体。",
            knowledgePoint = "细胞生理",
        ),
        Question(
            sourceNumber = "2",
            type = QuestionType.SINGLE,
            stem = "成年人安静状态下的正常心率范围通常为？",
            options = options(
                "20～40 次/分",
                "60～100 次/分",
                "100～140 次/分",
                "140～180 次/分",
            ),
            answer = "B",
            explanation = "教材常用正常范围为 60～100 次/分，具体判断仍需结合情境。",
            knowledgePoint = "循环生理",
        ),
        Question(
            sourceNumber = "3",
            type = QuestionType.MULTI,
            stem = "下列哪些属于人体免疫系统的组成部分？",
            options = options("淋巴结", "脾", "胸腺", "骨髓"),
            answer = "ABCD",
            explanation = "淋巴结、脾、胸腺和骨髓均属于免疫系统相关器官或组织。",
            knowledgePoint = "免疫系统",
        ),
        Question(
            sourceNumber = "4",
            type = QuestionType.JUDGE,
            stem = "在标准大气压下，纯水在 100 摄氏度沸腾。",
            answer = "true",
            explanation = "这是基础物理化学常识，教材示例。",
            knowledgePoint = "物理基础",
        ),
        Question(
            sourceNumber = "5",
            type = QuestionType.FILL,
            stem = "血液中运输氧气的主要蛋白是____。",
            answer = "血红蛋白",
            explanation = "血红蛋白承担血液中大部分氧的运输。",
            knowledgePoint = "血液生理",
        ),
        Question(
            sourceNumber = "6",
            type = QuestionType.SINGLE,
            stem = "以下哪项属于教材中描述的反射弧组成部分？",
            options = options("感受器", "效应器", "神经中枢", "以上均是"),
            answer = "D",
            explanation = "反射弧包括感受器、传入神经、神经中枢、传出神经和效应器。",
            knowledgePoint = "神经系统",
        ),
        Question(
            sourceNumber = "7",
            type = QuestionType.MULTI,
            stem = "下列属于标准预防基本措施的有？",
            options = options("手卫生", "合理使用防护用品", "安全注射", "规范处理污染物"),
            answer = "ABCD",
            explanation = "标准预防包含手卫生、防护用品、安全注射和环境与污染物管理。",
            knowledgePoint = "感染控制",
        ),
        Question(
            sourceNumber = "8",
            type = QuestionType.JUDGE,
            stem = "肺动脉通常运输含氧量较低的血液。",
            answer = "true",
            explanation = "肺循环中肺动脉将含氧量较低的血液送往肺。",
            knowledgePoint = "循环系统",
        ),
        Question(
            sourceNumber = "9",
            type = QuestionType.FILL,
            stem = "肾脏结构和功能的基本单位是____。",
            answer = "肾单位",
            explanation = "肾单位由肾小体和肾小管构成。",
            knowledgePoint = "泌尿系统",
        ),
        Question(
            sourceNumber = "10",
            type = QuestionType.SINGLE,
            stem = "用于估算心输出量的基本公式是？",
            options = options(
                "心率 × 每搏输出量",
                "血压 × 心率",
                "每搏输出量 ÷ 心率",
                "呼吸频率 × 潮气量",
            ),
            answer = "A",
            explanation = "心输出量通常用心率乘以每搏输出量估算。",
            knowledgePoint = "循环生理",
        ),
    )

    private fun options(vararg texts: String): List<QuestionOption> =
        texts.mapIndexed { index, text ->
            QuestionOption(('A' + index).toString(), text)
        }
}
