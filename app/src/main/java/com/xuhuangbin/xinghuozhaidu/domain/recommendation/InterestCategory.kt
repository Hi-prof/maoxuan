package com.xuhuangbin.xinghuozhaidu.domain.recommendation

enum class InterestCategory(val id: String, val label: String) {
    SelfGrowth("self_growth", "自我成长"),
    Learning("learning", "学习求知"),
    LifeWisdom("life_wisdom", "人生智慧"),
    Ideals("ideals", "理想奋斗"),
    Courage("courage", "勇气行动"),
    Practice("practice", "实践求真"),
    Philosophy("philosophy", "哲学思辨"),
    Labor("labor", "劳动创造"),
    Relationships("relationships", "人际关系"),
    PeopleSociety("people_society", "人民社会"),
    History("history", "历史时代"),
    Poetry("poetry", "诗词文学"),
    ;

    companion object {
        private val byId = entries.associateBy(InterestCategory::id)

        fun fromId(id: String): InterestCategory? = byId[id]
    }
}
