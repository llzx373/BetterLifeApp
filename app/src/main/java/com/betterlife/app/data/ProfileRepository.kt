package com.betterlife.app.data

import com.betterlife.app.data.db.ProfileEntity
import com.betterlife.app.data.db.ProfileDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/*
 * 用户档案领域模型。所有枚举的 key 与规则文件 when 字段里的取值一一对应，
 * 规则匹配只认 key（见 Profile.fieldValues）。
 */

enum class AgeRange(val key: String) { A18_25("18-25"), A26_35("26-35"), A36_45("36-45"), A46_60("46-60"), A60_PLUS("60+") }
enum class Gender(val key: String) { MALE("male"), FEMALE("female"), OTHER("other") }
enum class Smoking(val key: String) { YES("yes"), QUIT("quit"), NO("no") }
enum class Alcohol(val key: String) { OFTEN("often"), SOMETIMES("sometimes"), NO("no") }
enum class SugaryDrinks(val key: String) { DAILY("daily"), SOMETIMES("sometimes"), NO("no") }
enum class Exercise(val key: String) { NONE("none"), LOW("low"), OK("ok") }
enum class Chronic(val key: String) { HYPERTENSION("hypertension"), DIABETES("diabetes"), KIDNEY("kidney"), HEART("heart"), OTHER("other") }
enum class Occupation(val key: String) { PROGRAMMER("programmer"), STUDENT("student"), OTHER("other") }
enum class Housing(val key: String) { RENT("rent"), OWN("own"), FAMILY("family") }
enum class Children(val key: String) { NONE("none"), BABY("baby"), SCHOOL("school") }
enum class Goal(val key: String) { HEALTH("health"), MONEY("money"), TIME("time"), CAREER("career"), FAMILY("family"), RELAX("relax") }

data class Profile(
    val ageRange: AgeRange = AgeRange.A26_35,
    val gender: Gender = Gender.OTHER,
    val smoking: Smoking = Smoking.NO,
    val secondhandSmoke: Boolean = false,
    val alcohol: Alcohol = Alcohol.NO,
    val betelNut: Boolean = false,
    val sugaryDrinks: SugaryDrinks = SugaryDrinks.NO,
    val exercise: Exercise = Exercise.NONE,
    val sleepShort: Boolean = false,
    val chronic: Set<Chronic> = emptySet(),
    val occupation: Occupation = Occupation.OTHER,
    val financialStress: Boolean = false,
    val housing: Housing = Housing.RENT,
    val children: Children = Children.NONE,
    val hasElderly: Boolean = false,
    val pregnant: Boolean = false,
    val planningAbroad: Boolean = false,
    val goals: Set<Goal> = emptySet(),
) {
    /**
     * 规则 when 条件的字段取值集合：
     * 单选字段 = 单元素集合；布尔字段 = "true"/"false"；多选字段 = 全部选中值。
     * chronic 为空（无慢病）时产生 "none"，让 when {"chronic": ["none"]} 的规则可命中。
     */
    fun fieldValues(field: String): Set<String> = when (field) {
        "ageRange" -> setOf(ageRange.key)
        "gender" -> setOf(gender.key)
        "smoking" -> setOf(smoking.key)
        "secondhandSmoke" -> setOf(secondhandSmoke.toString())
        "alcohol" -> setOf(alcohol.key)
        "betelNut" -> setOf(betelNut.toString())
        "sugaryDrinks" -> setOf(sugaryDrinks.key)
        "exercise" -> setOf(exercise.key)
        "sleepShort" -> setOf(sleepShort.toString())
        "chronic" -> if (chronic.isEmpty()) setOf("none") else chronic.mapTo(mutableSetOf()) { it.key }
        "occupation" -> setOf(occupation.key)
        "financialStress" -> setOf(financialStress.toString())
        "housing" -> setOf(housing.key)
        "children" -> setOf(children.key)
        "hasElderly" -> setOf(hasElderly.toString())
        "pregnant" -> setOf(pregnant.toString())
        "planningAbroad" -> setOf(planningAbroad.toString())
        "goals" -> goals.mapTo(mutableSetOf()) { it.key }
        else -> emptySet()
    }

    /** 规则命中判定：when 为空 = 普惠；否则所有字段都匹配（多选字段取交集非空） */
    fun matches(cond: Map<String, List<String>>): Boolean =
        cond.all { (field, values) -> fieldValues(field).any { it in values } }

    /** 给 AI 提示词用的档案摘要 */
    fun describe(): String = buildString {
        append("年龄段 ${ageRange.key}")
        append("，性别 ${gender.key}")
        if (smoking == Smoking.YES) append("，吸烟") else if (smoking == Smoking.QUIT) append("，已戒烟")
        if (secondhandSmoke) append("，常接触二手烟")
        if (alcohol == Alcohol.OFTEN) append("，经常饮酒") else if (alcohol == Alcohol.SOMETIMES) append("，偶尔饮酒")
        if (betelNut) append("，嚼槟榔")
        if (sugaryDrinks == SugaryDrinks.DAILY) append("，每天含糖饮料")
        if (exercise == Exercise.NONE) append("，几乎不运动")
        if (sleepShort) append("，睡眠不足")
        if (chronic.isNotEmpty()) append("，慢性病：${chronic.joinToString("、") { it.key }}")
        if (occupation == Occupation.PROGRAMMER) append("，程序员") else if (occupation == Occupation.STUDENT) append("，学生")
        if (financialStress) append("，经济压力大")
        if (pregnant) append("，孕期")
        if (goals.isNotEmpty()) append("，目标：${goals.joinToString("、") { it.key }}")
    }
}

/** 目标勾选/取消勾选：checked=true 加入 goals、false 移除，幂等 */
fun Profile.toggleGoal(goal: Goal, checked: Boolean): Profile =
    copy(goals = if (checked) goals + goal else goals - goal)

// ---------- Profile <-> ProfileEntity 映射（纯函数，可单测） ----------

private inline fun <reified T : Enum<T>> enumByKey(key: String, default: T, keyOf: (T) -> String): T =
    enumValues<T>().firstOrNull { keyOf(it) == key } ?: default

fun ProfileEntity.toProfile(): Profile = Profile(
    ageRange = enumByKey(ageRange, AgeRange.A26_35) { it.key },
    gender = enumByKey(gender, Gender.OTHER) { it.key },
    smoking = enumByKey(smoking, Smoking.NO) { it.key },
    secondhandSmoke = secondhandSmoke,
    alcohol = enumByKey(alcohol, Alcohol.NO) { it.key },
    betelNut = betelNut,
    sugaryDrinks = enumByKey(sugaryDrinks, SugaryDrinks.NO) { it.key },
    exercise = enumByKey(exercise, Exercise.NONE) { it.key },
    sleepShort = sleepShort,
    chronic = chronic.split(',').filter { it.isNotBlank() }
        .mapNotNull { k -> enumValues<Chronic>().firstOrNull { it.key == k } }.toSet(),
    occupation = enumByKey(occupation, Occupation.OTHER) { it.key },
    financialStress = financialStress,
    housing = enumByKey(housing, Housing.RENT) { it.key },
    children = enumByKey(children, Children.NONE) { it.key },
    hasElderly = hasElderly,
    pregnant = pregnant,
    planningAbroad = planningAbroad,
    goals = goals.split(',').filter { it.isNotBlank() }
        .mapNotNull { k -> enumValues<Goal>().firstOrNull { it.key == k } }.toSet(),
)

fun Profile.toEntity(): ProfileEntity = ProfileEntity(
    ageRange = ageRange.key,
    gender = gender.key,
    smoking = smoking.key,
    secondhandSmoke = secondhandSmoke,
    alcohol = alcohol.key,
    betelNut = betelNut,
    sugaryDrinks = sugaryDrinks.key,
    exercise = exercise.key,
    sleepShort = sleepShort,
    chronic = chronic.joinToString(",") { it.key },
    occupation = occupation.key,
    financialStress = financialStress,
    housing = housing.key,
    children = children.key,
    hasElderly = hasElderly,
    pregnant = pregnant,
    planningAbroad = planningAbroad,
    goals = goals.joinToString(",") { it.key },
)

/** 档案存取的窄接口：ViewModel 只依赖它，测试里可用内存 fake 替换 */
interface ProfileRepo {
    val profileFlow: Flow<Profile?>
    suspend fun save(profile: Profile)
}

class ProfileRepository(private val dao: ProfileDao) : ProfileRepo {
    override val profileFlow: Flow<Profile?> = dao.profileFlow().map { it?.toProfile() }

    suspend fun getProfile(): Profile? = dao.getProfile()?.toProfile()

    override suspend fun save(profile: Profile) = dao.upsert(profile.toEntity())
}
