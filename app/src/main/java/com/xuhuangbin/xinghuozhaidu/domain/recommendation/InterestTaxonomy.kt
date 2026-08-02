package com.xuhuangbin.xinghuozhaidu.domain.recommendation

import com.xuhuangbin.xinghuozhaidu.domain.model.QuoteCard

object InterestTaxonomy {
    private val themesByCategory = mapOf(
        InterestCategory.SelfGrowth to setOf(
            "成长", "人的发展", "青年", "青年担当", "品格", "谦逊", "坚韧", "进取",
            "改错", "自我批评", "人才", "重新出发",
        ),
        InterestCategory.Learning to setOf(
            "学习", "学风", "认识", "经验", "写作", "表达", "想象力", "向群众学习",
            "研究方法", "文风", "普及提高",
        ),
        InterestCategory.LifeWisdom to setOf(
            "人生", "自由", "价值", "时间", "从容", "定力", "乐观", "长期主义", "长远眼光",
            "开放视野", "世界视野", "无私",
        ),
        InterestCategory.Ideals to setOf(
            "理想", "奋斗", "信念", "信心", "胜利", "目标", "坚持", "奉献", "气魄",
            "时代担当", "战略信心", "艰苦奋斗",
        ),
        InterestCategory.Courage to setOf(
            "勇气", "行动", "主动", "主动权", "担当", "责任", "攀登", "把握时机",
            "战略主动", "集中力量", "原则", "困难", "独立自主",
        ),
        InterestCategory.Practice to setOf(
            "实践", "理论与实践", "理论联系实际", "调查研究", "社会调查", "实事求是",
            "工作方法", "方法论", "求真", "实践方向",
        ),
        InterestCategory.Philosophy to setOf(
            "马原思考", "历史唯物主义", "辩证思维", "辩证法", "矛盾分析", "内外因", "认识论",
            "人的作用", "动态判断", "全局思维",
        ),
        InterestCategory.Labor to setOf(
            "劳动", "经济建设", "建设", "工业建设", "工作", "工作重点", "自力更生",
        ),
        InterestCategory.Relationships to setOf(
            "关系", "社会关系", "团结", "群众关系", "群众联系", "共同目标", "组织",
            "组织领导", "领导方法",
        ),
        InterestCategory.PeopleSociety to setOf(
            "人民立场", "群众路线", "群众生活", "群众", "人民", "为人民服务", "人民利益",
            "人民权利", "群众力量", "群众运动", "党的建设", "政策", "宣传", "政策传播",
            "作风", "作风建设", "组织纪律", "政权", "人民军队", "人民战争", "统一战线",
            "国际主义", "阶级分析", "武装斗争", "新民主主义", "整风", "国情",
        ),
        InterestCategory.History to setOf(
            "历史", "历史进程", "历史画面", "历史评价", "时代变化", "长征", "抗战", "山河",
            "形势判断", "战略", "革命方法",
        ),
        InterestCategory.Poetry to setOf(
            "文艺", "文化", "人民文化", "写作", "表达", "想象力", "文风",
        ),
    )

    fun categoriesFor(card: QuoteCard): Set<InterestCategory> = buildSet {
        themesByCategory.forEach { (category, themes) ->
            if (card.themes.any(themes::contains)) add(category)
        }
        when (card.series) {
            "毛泽东诗词" -> add(InterestCategory.Poetry)
            "马原思考" -> add(InterestCategory.Philosophy)
        }
    }
}
