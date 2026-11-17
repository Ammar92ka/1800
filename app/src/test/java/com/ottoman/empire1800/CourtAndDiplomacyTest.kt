package com.ottoman.empire1800

import com.ottoman.empire1800.data.anchorsFor
import com.ottoman.empire1800.data.effectiveAbout
import com.ottoman.empire1800.data.frontierRegionDefs
import com.ottoman.empire1800.data.projectDef
import com.ottoman.empire1800.data.subProvinceDefs
import com.ottoman.empire1800.engine.Calibrator
import com.ottoman.empire1800.engine.GameEngine
import com.ottoman.empire1800.engine.RoundParser
import com.ottoman.empire1800.models.Decision
import com.ottoman.empire1800.models.DecisionCategory
import com.ottoman.empire1800.models.DecisionOption
import com.ottoman.empire1800.models.EconomicSystemKey
import com.ottoman.empire1800.models.FactoryKey
import com.ottoman.empire1800.models.GameState
import com.ottoman.empire1800.models.RoundPlan
import com.ottoman.empire1800.services.CrisisRules
import com.ottoman.empire1800.services.DebtRules
import com.ottoman.empire1800.services.DeepEconomyRules
import com.ottoman.empire1800.services.DiplomacyRules
import com.ottoman.empire1800.services.EconomyRules
import com.ottoman.empire1800.services.FrontierRules
import com.ottoman.empire1800.services.GrandVizierRules
import com.ottoman.empire1800.services.IndustryRules
import com.ottoman.empire1800.services.MilestoneRules
import com.ottoman.empire1800.services.PowerRules
import com.ottoman.empire1800.services.ProjectRules
import com.ottoman.empire1800.services.ProvinceRules
import com.ottoman.empire1800.services.SpyRules
import com.ottoman.empire1800.services.WarRules
import com.ottoman.empire1800.services.buildRoundPrompt
import com.ottoman.empire1800.services.costShare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CourtAndDiplomacyTest {

    @Test
    fun powerAppeaseAndProjectsUpdateInfluenceAndSatisfaction() {
        val s = GameState(treasury = 20_000_000.0)
        val jan = s.powers["janissaries"]!!
        val satBefore = jan.satisfaction
        val res = PowerRules.appease(s, "janissaries")
        assertTrue(res.ok)
        assertTrue(jan.satisfaction > satBefore)
        assertEquals(1, s.courtActions)

        val proj = PowerRules.startProject(s, "janissaries", "reform")
        assertTrue(proj.ok)
        assertTrue(PowerRules.hasProject(s, "janissaries"))
        assertEquals(0, s.courtActions)

        // Out of court actions rejects another court action
        val extra = PowerRules.appease(s, "ulema")
        assertFalse(extra.ok)

        val infBefore = jan.influence
        for (i in 0 until 6) {
            s.round = i
            PowerRules.step(s, emergency = false)
        }
        assertTrue(jan.influence < infBefore)
        assertFalse(PowerRules.hasProject(s, "janissaries"))
    }

    @Test
    fun dissolveIsDeterministicPerSeedAndLowDangerSucceeds() {
        val s1 = GameState(seed = 123, treasury = 20_000_000.0)
        val s2 = GameState(seed = 123, treasury = 20_000_000.0)
        s1.powers["tax_farmers"]!!.satisfaction = 80.0
        s1.powers["tax_farmers"]!!.influence = 20.0
        s2.powers["tax_farmers"]!!.satisfaction = 80.0
        s2.powers["tax_farmers"]!!.influence = 20.0

        assertEquals("خطر منخفض", PowerRules.dissolveRisk(s1.powers["tax_farmers"]!!))
        val r1 = PowerRules.dissolve(s1, "tax_farmers")
        val r2 = PowerRules.dissolve(s2, "tax_farmers")
        assertTrue(r1.ok)
        assertEquals(r1.ok, r2.ok)
        assertTrue(s1.powers["tax_farmers"]!!.dissolved)
    }

    @Test
    fun spyLevelsGenerateAndRevealReportsCappedAtMaxKept() {
        val s = GameState(treasury = 20_000_000.0)
        assertTrue(DiplomacyRules.setSpyLevel(s, "russia", 2).ok)
        assertTrue(DiplomacyRules.setSpyLevel(s, "britain", 2).ok)
        assertTrue(DiplomacyRules.setDomesticSpy(s, 2).ok)
        assertEquals(2, s.countries["russia"]!!.intelQuality)

        for (i in 0 until 12) {
            s.round = i
            SpyRules.step(s)
        }
        assertTrue(s.intel.isNotEmpty())
        assertTrue(s.intel.size <= SpyRules.MAX_KEPT)
        assertTrue(s.intel.any { it.revealed })
    }

    @Test
    fun embassiesCreditFactorAndTradeTreatyWithPerk() {
        val s = GameState(treasury = 20_000_000.0)
        assertTrue(DiplomacyRules.openEmbassy(s, "britain").ok)
        assertEquals(3, s.countries["britain"]!!.intelQuality)

        // High relations raise creditFactor
        s.countries["britain"]!!.relation = 90.0
        s.countries["france"]!!.relation = 85.0
        s.countries["austria"]!!.relation = 80.0
        assertTrue(DiplomacyRules.creditFactor(s) > 1.15)

        // Treaty with Britain drops France slightly and adds delayed customs increase (1.5x with trade perk)
        s.completed.add("ports") // perk = trade
        val franceBefore = s.countries["france"]!!.relation
        val treaty = DiplomacyRules.tradeTreaty(s, "britain")
        assertTrue(treaty.ok)
        assertTrue(s.countries["france"]!!.relation < franceBefore)
        assertEquals(1, s.pending.size)
        val expectedGain = s.budget.totalRevenue * DiplomacyRules.TREATY_CUSTOMS_GAIN * 1.5
        assertEquals(expectedGain, s.pending.first().lines.first().delta, 1.0)
    }

    @Test
    fun repayDebtReducesTreasuryAndDebtPrioritizingInternalFirst() {
        val engine = GameEngine()
        val s = GameState(treasury = 5_000_000.0, debt = 8_000_000.0, foreignDebt = 3_000_000.0)
        // Internal = 5M, foreign = 3M; paying 5M clears internal and leaves 3M foreign
        val paid = engine.repayDebt(s, 5_000_000.0)
        assertEquals(5_000_000.0, paid, 0.01)
        assertEquals(0.0, s.treasury, 0.01)
        assertEquals(3_000_000.0, s.debt, 0.01)
        assertEquals(3_000_000.0, s.foreignDebt, 0.01)
        assertEquals(0.0, DebtRules.internalDebt(s), 0.01)
    }

    @Test
    fun janissaryDissolutionRequiresReadinessAndTriggersSpecificOutcomes() {
        val unprepared = GameState(seed = 42, treasury = 50_000_000.0)
        val r0 = PowerRules.janissaryReadiness(unprepared)
        assertTrue(r0.hasUlema)
        assertFalse(r0.hasMilitary)
        assertFalse(r0.lowInfluence)
        assertTrue(r0.successChance < 0.20)
        assertEquals("تمرد مرجح", PowerRules.dissolveRisk(unprepared.powers["janissaries"]!!, unprepared))

        val leg0 = unprepared.legitimacy
        val army0 = unprepared.army
        val failRes = PowerRules.dissolve(unprepared, "janissaries")
        assertFalse(failRes.ok)
        assertFalse(unprepared.powers["janissaries"]!!.dissolved)
        assertEquals(leg0 - 10, unprepared.legitimacy)
        assertEquals(army0 - 8, unprepared.army)

        val prepared = GameState(seed = 42, treasury = 50_000_000.0)
        prepared.powers["ulema"]!!.satisfaction = 70.0
        prepared.army = 65
        prepared.powers["janissaries"]!!.influence = 45.0
        assertEquals("جاهز للحل", PowerRules.dissolveRisk(prepared.powers["janissaries"]!!, prepared))

        val armySpend0 = prepared.budget.lineValue("army")
        val okRes = PowerRules.dissolve(prepared, "janissaries")
        assertTrue(okRes.ok)
        assertTrue(prepared.powers["janissaries"]!!.dissolved)
        assertEquals(55, prepared.army)
        assertTrue(prepared.budget.lineValue("army") < armySpend0)
        assertTrue(prepared.world.reforms.any { it.contains("الانكشارية") })

        val modernized = GameState(seed = 42, treasury = 50_000_000.0)
        modernized.completed.add("modern_army")
        modernized.powers["ulema"]!!.satisfaction = 70.0
        modernized.powers["janissaries"]!!.influence = 45.0
        modernized.army = 60
        assertTrue(PowerRules.dissolve(modernized, "janissaries").ok)
        assertEquals(60, modernized.army)
    }

    @Test
    fun powerSpecificDissolutionsAndProjectCompletionRewardsWorkWithScaling() {
        // tax_farmers
        val sTax = GameState(treasury = 50_000_000.0, ppy = 4)
        sTax.powers["tax_farmers"]!!.satisfaction = 90.0
        sTax.powers["tax_farmers"]!!.influence = 20.0
        val corr0 = sTax.econ.corruption
        assertTrue(PowerRules.dissolve(sTax, "tax_farmers").ok)
        assertEquals(corr0 - 8.0, sTax.econ.corruption, 0.01)
        assertEquals(1, sTax.pending.size)
        assertEquals(sTax.round + 4, sTax.pending.first().dueRound)
        assertEquals("land", sTax.pending.first().lines.first().line)

        // mamluks slows egypt drift
        val sMam = GameState(treasury = 50_000_000.0)
        sMam.powers["mamluks"]!!.satisfaction = 90.0
        sMam.powers["mamluks"]!!.influence = 20.0
        sMam.econ.provinces["egypt"]!!.autonomy = 40.0
        assertTrue(PowerRules.dissolve(sMam, "mamluks").ok)
        assertEquals(30.0, sMam.econ.provinces["egypt"]!!.autonomy, 0.01)

        val sMamActive = GameState()
        sMamActive.econ.provinces["egypt"]!!.autonomy = 30.0
        sMam.econ.provinces["egypt"]!!.autonomy = 30.0
        ProvinceRules.step(sMamActive)
        ProvinceRules.step(sMam)
        assertTrue(sMam.econ.provinces["egypt"]!!.autonomy < sMamActive.econ.provinces["egypt"]!!.autonomy)

        // reform reward & danger multiplier
        val sRef = GameState(treasury = 50_000_000.0, ppy = 4)
        val leg0 = sRef.legitimacy
        assertTrue(PowerRules.startProject(sRef, "janissaries", "reform").ok)
        assertEquals(12, sRef.projects.first().remaining)
        for (i in 0 until 12) {
            sRef.round = minOf(i, 7)
            PowerRules.step(sRef, emergency = false)
        }
        assertTrue(sRef.powers["janissaries"]!!.reformed)
        assertEquals(leg0 + 2, sRef.legitimacy)
        val reformedDanger = sRef.powers["janissaries"]!!.danger
        sRef.powers["janissaries"]!!.reformed = false
        assertTrue(reformedDanger < sRef.powers["janissaries"]!!.danger)

        // merge reward & save/load roundtrip
        val sMerge = GameState(treasury = 50_000_000.0)
        val army0 = sMerge.army
        assertTrue(PowerRules.startProject(sMerge, "janissaries", "merge").ok)
        for (i in 0 until 5) {
            sMerge.round = minOf(i, 3)
            PowerRules.step(sMerge, emergency = false)
        }
        assertTrue(sMerge.powers["janissaries"]!!.merged)
        assertTrue(sMerge.powers["janissaries"]!!.dissolved)
        assertEquals(army0 + 5, sMerge.army)
        val restored = GameState.fromJson(sMerge.toJson())!!
        assertTrue(restored.powers["janissaries"]!!.merged)
    }

    @Test
    fun aiPromptCalibratorAndCatalogSyncWithDissolvedPowers() {
        val s = GameState(treasury = 50_000_000.0)
        s.round = 52 // year 1826 so modern_army (fromYear 1805) is available in catalog
        s.powers["ulema"]!!.satisfaction = 75.0
        s.army = 65
        s.powers["janissaries"]!!.influence = 40.0
        assertTrue(PowerRules.dissolve(s, "janissaries").ok)

        assertTrue(anchorsFor(1826).any { it.contains("الواقعة الخيرية") })
        assertFalse(anchorsFor(1826, s).any { it.contains("الواقعة الخيرية") })

        val modern = projectDef("modern_army")!!
        assertTrue(modern.about.contains("يغضب الانكشارية"))
        assertFalse(modern.effectiveAbout(s).contains("يغضب الانكشارية"))
        assertTrue(ProjectRules.suggestions(s, 5).any { it.def.key == "modern_army" })

        val prompt = buildRoundPrompt(s)
        assertTrue(prompt.contains("DISSOLVED POWERS"))
        assertTrue(prompt.contains("janissaries=الانكشارية"))

        val opt = DecisionOption(
            text = "خيار",
            result = "نتيجة",
            powerMood = mapOf("janissaries" to 5, "ulema" to 3),
            dissolve = "janissaries",
        )
        val dec = Decision(DecisionCategory.INTERNAL, "عنوان", "مكان", "مشهد", "معضلة", listOf(opt, opt, opt))
        val plan = RoundPlan("رأس", "ملخص", "", null, listOf(dec, dec, dec))
        val calibrated = Calibrator.calibrate(plan, s)
        assertFalse(calibrated.decisions[0].options[0].powerMood.containsKey("janissaries"))
        assertEquals(3, calibrated.decisions[0].options[0].powerMood["ulema"])
        assertEquals("", calibrated.decisions[0].options[0].dissolve)
    }

    @Test
    fun grandVizierEconomyActionsAndSpecialOpsWorkInParity() {
        val s = GameState(seed = 101, treasury = 50_000_000.0, ppy = 4)
        s.courtActions = 6
        assertEquals("traditional", s.grandVizier)

        // Appoint economist -> -10% project cost & +5 rating
        val normalCost = ProjectRules.totalCost(s, projectDef("printing")!!, 12)
        assertTrue(GrandVizierRules.appoint(s, "economist").ok)
        val econCost = ProjectRules.totalCost(s, projectDef("printing")!!, 12)
        assertTrue(econCost < normalCost)
        assertTrue(DebtRules.ratingFactors(s).any { it.label.contains("اقتصادي") && it.value == 5.0 })

        // Military vizier boosts war power & prompt mentions active vizier
        val basePower = WarRules.ourPower(s)
        assertTrue(GrandVizierRules.appoint(s, "military").ok)
        assertEquals(basePower * 1.10, WarRules.ourPower(s), 0.01)
        assertTrue(buildRoundPrompt(s).contains("صدر أعظم عسكري"))

        // Reformer reduces corruption drift
        s.grandVizier = "reformer"
        val corr0 = s.econ.corruption
        DeepEconomyRules.step(s, 0.0)
        assertTrue(s.econ.corruption < corr0)

        // Tactical Economy actions
        assertFalse(EconomyRules.grainSubsidy(s).ok)
        s.econ.grain = 150.0
        assertTrue(EconomyRules.grainSubsidy(s).ok)
        assertEquals(125.0, s.econ.grain, 0.01)

        assertTrue(EconomyRules.antiCorruption(s).ok)
        assertTrue(EconomyRules.palaceAusterity(s).ok)
        assertEquals(16, EconomyRules.austerityCooldownRemaining(s))
        assertFalse(EconomyRules.palaceAusterity(s).ok)

        // Differentiated domestic vs foreign spy previews, upkeep & special ops
        val dom01 = DiplomacyRules.spyCostPreview(s, 0, 1, isDomestic = true)
        val dom12 = DiplomacyRules.spyCostPreview(s, 1, 2, isDomestic = true)
        val dom02 = DiplomacyRules.spyCostPreview(s, 0, 2, isDomestic = true)
        assertEquals(costShare(s, 0.008), dom01.recruitCost, 0.01)
        assertEquals(costShare(s, 0.015), dom01.annualUpkeep, 0.01)
        assertEquals(costShare(s, 0.016), dom12.recruitCost, 0.01)
        assertEquals(costShare(s, 0.024), dom02.recruitCost, 0.01)
        assertEquals(costShare(s, 0.035), dom02.annualUpkeep, 0.01)

        val for01 = DiplomacyRules.spyCostPreview(s, 0, 1, isDomestic = false)
        val for12 = DiplomacyRules.spyCostPreview(s, 1, 2, isDomestic = false)
        val for02 = DiplomacyRules.spyCostPreview(s, 0, 2, isDomestic = false)
        assertEquals(costShare(s, 0.010), for01.recruitCost, 0.01)
        assertEquals(costShare(s, 0.012), for01.annualUpkeep, 0.01)
        assertEquals(costShare(s, 0.020), for12.recruitCost, 0.01)
        assertEquals(costShare(s, 0.030), for02.recruitCost, 0.01)
        assertEquals(costShare(s, 0.030), for02.annualUpkeep, 0.01)

        val admin0 = s.budget.spending["admin"] ?: 0.0
        val t0 = s.treasury
        assertTrue(DiplomacyRules.setDomesticSpy(s, 1).ok)
        assertEquals(costShare(s, 0.008), t0 - s.treasury, 0.01)
        assertEquals(costShare(s, 0.015), (s.budget.spending["admin"] ?: 0.0) - admin0, 0.01)
        val t1 = s.treasury
        assertTrue(DiplomacyRules.setDomesticSpy(s, 2).ok)
        assertEquals(costShare(s, 0.016), t1 - s.treasury, 0.01)
        assertEquals(costShare(s, 0.035), (s.budget.spending["admin"] ?: 0.0) - admin0, 0.01)

        val tDomOp = s.treasury
        assertTrue(DiplomacyRules.domesticSpecialOp(s).ok)
        assertEquals(costShare(s, 0.015), tDomOp - s.treasury, 0.01)

        val tFor = s.treasury
        assertTrue(DiplomacyRules.setSpyLevel(s, "russia", 2).ok)
        assertEquals(costShare(s, 0.030), tFor - s.treasury, 0.01)
        WarRules.declare(s, "russia", byUs = true)
        val wScore0 = WarRules.warWith(s, "russia")!!.score
        val tForOp = s.treasury
        assertTrue(DiplomacyRules.foreignSpecialOp(s, "russia").ok)
        assertEquals(costShare(s, 0.028), tForOp - s.treasury, 0.01)
        assertEquals(wScore0 + 5.0, WarRules.warWith(s, "russia")!!.score, 0.01)

        // dissolveSuccessChance + detail
        assertEquals(1.0, PowerRules.dissolveSuccessChance(s, s.powers["merchants"]!!), 0.001)
        val janFail = PowerRules.dissolve(s, "janissaries")
        assertFalse(janFail.ok)
        assertTrue(!janFail.detail.isNullOrEmpty())
    }

    @Test
    fun defensiveAlliancesTradeoffsAndPersistentDecrees() {
        val s = GameState(seed = 2026, treasury = 100_000_000.0)
        s.courtActions = 20

        // 1) Alliance gating & rival penalties
        s.countries["france"]!!.relation = 79.0
        assertFalse(DiplomacyRules.signAlliance(s, "france").ok)

        s.countries["britain"]!!.relation = 85.0
        val frBefore = s.countries["france"]!!.relation
        val basePower = WarRules.ourPower(s)
        assertTrue(DiplomacyRules.signAlliance(s, "britain").ok)
        assertTrue(s.countries["britain"]!!.alliance)
        assertEquals(frBefore - 8.0, s.countries["france"]!!.relation, 0.01)
        assertEquals(basePower * 1.12, WarRules.ourPower(s), 0.01)
        assertTrue(s.world.alliances.any { it.contains("بريطانيا") })

        // Low relation breaks alliance
        s.countries["france"]!!.relation = 85.0
        s.countries["germany"]!!.relation = 60.0
        assertTrue(DiplomacyRules.signAlliance(s, "france").ok)
        s.countries["france"]!!.relation = 40.0
        val notes = DiplomacyRules.step(s)
        assertFalse(s.countries["france"]!!.alliance)
        assertTrue(notes.any { it.contains("فرنسا") })

        // War against active ally revokes alliance
        WarRules.declare(s, "britain", byUs = false)
        assertFalse(s.countries["britain"]!!.alliance)

        // 2) Interconnected trade-offs
        val rus0 = s.countries["russia"]!!.relation
        val aus0 = s.countries["austria"]!!.relation
        assertTrue(ProvinceRules.suppress(s, "rumelia").ok)
        assertEquals(rus0 - 4.0, s.countries["russia"]!!.relation, 0.01)
        assertEquals(aus0 - 4.0, s.countries["austria"]!!.relation, 0.01)

        s.econ.provinces["egypt"]!!.autonomy = 35.0
        s.econ.provinces["anatolia"]!!.autonomy = 3.0
        s.econ.provinces["anatolia"]!!.mood = 65.0
        assertTrue(ProvinceRules.privileges(s, "syria").ok)
        assertEquals(37.0, s.econ.provinces["egypt"]!!.autonomy, 0.01)
        assertEquals(3.0, s.econ.provinces["anatolia"]!!.autonomy, 0.01)

        val merch0 = s.powers["merchants"]!!.satisfaction
        assertTrue(PowerRules.appease(s, "janissaries").ok)
        assertEquals(merch0 - 2.0, s.powers["merchants"]!!.satisfaction, 0.01)
        s.powers["merchants"]!!.dissolved = true
        val ul0 = s.powers["ulema"]!!.satisfaction
        assertTrue(PowerRules.appease(s, "janissaries").ok)
        assertEquals(ul0 - 2.0, s.powers["ulema"]!!.satisfaction, 0.01)

        // 3) Persistent Royal Decrees, prompt inclusion & JSON roundtrip
        assertTrue(s.decrees.isNotEmpty())
        s.countries["austria"]!!.relation = 85.0
        assertTrue(DiplomacyRules.signAlliance(s, "austria").ok)
        val prompt = buildRoundPrompt(s)
        assertTrue(prompt.contains("تحالفات دفاعية قائمة"))
        assertTrue(prompt.contains("فرمانات السلطان الأخيرة"))

        for (i in 0 until 10) {
            GrandVizierRules.appoint(s, if (i % 2 == 0) "reformer" else "economist")
            s.courtActions = 5
        }
        assertEquals(8, s.decrees.size)

        val restored = GameState.fromJson(s.toJson())
        assertTrue(restored.countries["austria"]!!.alliance)
        assertEquals(8, restored.decrees.size)

        val legacyJson = s.toJson().apply { remove("decrees") }
        val restoredLegacy = GameState.fromJson(legacyJson)
        assertTrue(restoredLegacy.decrees.isEmpty())
    }

    @Test
    fun crisisAlertsMilestonesAndLastReportPersistence() {
        // 1) CrisisAlerts evaluation & Court tab routing
        val sc = GameState(treasury = 50_000_000.0)
        sc.powers["janissaries"]!!.influence = 30.0
        sc.powers["mamluks"]!!.influence = 30.0
        assertEquals(0, CrisisRules.evaluate(sc).size)

        sc.powers["janissaries"]!!.influence = 90.0
        sc.powers["janissaries"]!!.satisfaction = 20.0
        sc.econ.provinces["egypt"]!!.autonomy = 75.0
        sc.econ.grain = 160.0
        sc.econ.inflation = 9.0
        WarRules.declare(sc, "russia", byUs = false)
        sc.wars.first().score = -25.0
        val crises = CrisisRules.evaluate(sc)
        assertEquals(4, crises.size)
        assertEquals(0, crises.first { it.id == "power_danger" }.courtTabIndex)
        assertEquals(4, crises.first { it.id == "province_autonomy" }.courtTabIndex)
        assertEquals(3, crises.first { it.id == "economic_crisis" }.courtTabIndex)
        assertEquals(1, crises.first { it.id == "losing_war" }.courtTabIndex)

        // 2) All 6 Milestones unlock & persist cleanly
        val sm = GameState(seed = 42, treasury = 100_000_000.0)
        sm.powers["ulema"]!!.satisfaction = 70.0
        sm.army = 65
        sm.powers["janissaries"]!!.influence = 40.0
        assertTrue(PowerRules.dissolve(sm, "janissaries").ok)
        assertTrue(sm.milestones.contains("abolish_janissaries"))

        sm.completed.add("railway")
        MilestoneRules.evaluate(sm)
        assertTrue(sm.milestones.contains("build_railway"))

        sm.round = 100 // 1850
        MilestoneRules.evaluate(sm)
        assertTrue(sm.milestones.contains("survive_50_years"))

        sm.foreignDebt = 1_000_000.0
        sm.debt = 1_000_000.0
        MilestoneRules.evaluate(sm)
        assertTrue(sm.hadForeignDebt)
        GameEngine().repayDebt(sm, 1_000_000.0)
        assertTrue(sm.milestones.contains("clear_foreign_debt"))

        WarRules.declare(sm, "persia", byUs = true)
        WarRules.conclude(sm, sm.wars.first(), 25.0)
        assertTrue(sm.milestones.contains("win_major_war"))

        sm.round = 150 // 1875 with all 6 provinces intact
        MilestoneRules.evaluate(sm)
        assertTrue(sm.milestones.contains("preserve_empire_1875"))
        assertEquals(6, sm.milestones.size)

        // 3) Resolve round saves lastFullReport and round-trips through JSON cleanly + legacy fallback
        val engine = GameEngine()
        val opt = DecisionOption(text = "خيار", result = "نتيجة")
        val dec = Decision(DecisionCategory.INTERNAL, "عنوان", "مكان", "مشهد", "معضلة", listOf(opt, opt, opt))
        sm.plan = RoundPlan("رأس", "ملخص", "", null, listOf(dec, dec, dec))
        sm.picks = mutableListOf(0, 0, 0)
        val rep = engine.resolveRound(sm)
        assertNotNull(sm.lastFullReport)
        assertEquals(rep.period, sm.lastFullReport!!.period)

        val restored = GameState.fromJson(sm.toJson())
        assertEquals(6, restored.milestones.size)
        assertNotNull(restored.lastFullReport)
        assertEquals(rep.period, restored.lastFullReport!!.period)

        val legacyJson = sm.toJson().apply {
            remove("milestones")
            remove("hadForeignDebt")
            remove("wonMajorWar")
            remove("lastFullReport")
        }
        val restoredLegacy = GameState.fromJson(legacyJson)
        assertNull(restoredLegacy.lastFullReport)
        assertTrue(restoredLegacy.milestones.contains("abolish_janissaries"))
        assertTrue(restoredLegacy.milestones.contains("build_railway"))
    }

    @Test
    fun industryEconomicSystemsOilDiscoveryAndFrontierRegions() {
        assertEquals(25, subProvinceDefs.size)
        assertEquals(4, frontierRegionDefs.size)

        val s = GameState(ppy = 4, treasury = 200_000_000.0)
        s.courtActions = 50

        // 1) Resource & control gating + max level 3 + slot limits + demolish 20%*lvl refund
        assertFalse(IndustryRules.canBuildInSubProvince(s.econ, "anatolia_istanbul", FactoryKey.COAL_MINE, 1800).ok)
        assertFalse(IndustryRules.canBuildInSubProvince(s.econ, "anatolia_trabzon", FactoryKey.TRADE_DEPOT, 1800).ok)
        assertFalse(IndustryRules.canBuildInSubProvince(s.econ, "algeria_algiers", FactoryKey.TEXTILE, 1800).ok)

        assertTrue(IndustryRules.buildOrUpgradeFactory(s, "syria_aleppo", FactoryKey.TEXTILE).ok)
        assertTrue(IndustryRules.buildOrUpgradeFactory(s, "syria_aleppo", FactoryKey.TEXTILE).ok)
        assertTrue(IndustryRules.buildOrUpgradeFactory(s, "syria_aleppo", FactoryKey.TEXTILE).ok)
        assertFalse(IndustryRules.buildOrUpgradeFactory(s, "syria_aleppo", FactoryKey.TEXTILE).ok)
        assertTrue(IndustryRules.buildOrUpgradeFactory(s, "syria_aleppo", FactoryKey.PAPER_MILL).ok)
        assertFalse(IndustryRules.buildOrUpgradeFactory(s, "syria_aleppo", FactoryKey.FOOD_MILL).ok)

        val tBeforeScrap = s.treasury
        val expectedScrap = costShare(s, 0.055 * 0.2 * 3)
        assertTrue(IndustryRules.demolishFactory(s, "syria_aleppo", FactoryKey.TEXTILE).ok)
        assertEquals(expectedScrap, s.treasury - tBeforeScrap, 0.01)

        // 2) Paper mill discount (5%/lvl cap 25%) + literacy growth + coal 1.25x + arms power/cost + food mill
        val spendBase = EconomyRules.halfYearSpending(s.budget, 0.5, s.grandVizier, s.econ)
        repeat(2) { assertTrue(IndustryRules.buildOrUpgradeFactory(s, "syria_aleppo", FactoryKey.PAPER_MILL).ok) }
        assertEquals(0.15, IndustryRules.paperMillAdminDiscount(s.econ), 1e-6)
        assertTrue(EconomyRules.halfYearSpending(s.budget, 0.5, s.grandVizier, s.econ) < spendBase)
        repeat(3) { assertTrue(IndustryRules.buildOrUpgradeFactory(s, "anatolia_istanbul", FactoryKey.PAPER_MILL).ok) }
        assertEquals(0.25, IndustryRules.paperMillAdminDiscount(s.econ), 1e-6)

        val lit0 = s.demog.literacy
        IndustryRules.step(s)
        assertEquals(lit0 + 0.015 * 6 * 0.5, s.demog.literacy, 1e-6)

        val revBeforeCoal = IndustryRules.annualFactoryRevenue(s.econ, s.budget, 1800, s.demog.literacy)
        assertTrue(IndustryRules.buildOrUpgradeFactory(s, "anatolia_trabzon", FactoryKey.COAL_MINE).ok)
        assertTrue(IndustryRules.hasCoalPower(s.econ))
        assertTrue(IndustryRules.annualFactoryRevenue(s.econ, s.budget, 1800, s.demog.literacy) > revBeforeCoal * 1.25)

        val pwr0 = WarRules.ourPower(s)
        WarRules.declare(s, "russia", byUs = true)
        val warCost0 = WarRules.costs(s)
        assertTrue(IndustryRules.buildOrUpgradeFactory(s, "anatolia_izmir", FactoryKey.ARMS_FACTORY).ok)
        assertTrue(IndustryRules.buildOrUpgradeFactory(s, "anatolia_izmir", FactoryKey.ARMS_FACTORY).ok)
        assertEquals(0.05, IndustryRules.armsPowerBonus(s.econ), 1e-6)
        assertEquals(0.07, IndustryRules.armsCostDiscount(s.econ), 1e-6)
        assertEquals(pwr0 * 1.05, WarRules.ourPower(s), 0.01)
        assertTrue(WarRules.costs(s) < warCost0)

        assertTrue(IndustryRules.buildOrUpgradeFactory(s, "egypt_cairo", FactoryKey.FOOD_MILL).ok)
        s.econ.grain = 150.0
        val egyptMood0 = s.econ.provinces["egypt"]!!.mood
        IndustryRules.step(s)
        assertTrue(s.econ.grain < 150.0)
        assertEquals(egyptMood0 + 0.35 * 0.5, s.econ.provinces["egypt"]!!.mood, 1e-6)

        // 3) Oil discovery in Mosul >= 1890
        s.round = (1890 - 1800) * 4
        val iraqMood0 = s.econ.provinces["iraq"]!!.mood
        val oilNotes = IndustryRules.step(s)
        assertTrue(s.econ.oilDiscovered.contains("iraq_mosul"))
        assertEquals(iraqMood0 + 4.0, s.econ.provinces["iraq"]!!.mood, 1e-6)
        assertTrue(oilNotes.any { it.contains("الموصل") })
        assertTrue(IndustryRules.canBuildInSubProvince(s.econ, "iraq_mosul", FactoryKey.COAL_MINE, 1890).ok)

        // 4) Economic system cooldown (8 * k = 16 quarters), subsidies & clearing, protectionism & laissez-faire
        s.round = 40
        assertTrue(IndustryRules.setEconomicSystem(s, EconomicSystemKey.INTERVENTIONISM).ok)
        assertEquals(16, IndustryRules.econSystemCooldownRemaining(s))
        assertFalse(IndustryRules.setEconomicSystem(s, EconomicSystemKey.LAISSEZ_FAIRE).ok)

        assertTrue(IndustryRules.buildOrUpgradeFactory(s, "hijaz_jeddah", FactoryKey.FOOD_MILL).ok)
        assertTrue(IndustryRules.toggleSubsidy(s, "hijaz_jeddah", FactoryKey.FOOD_MILL).ok)
        assertEquals(18_000_000.0 * 0.002, IndustryRules.annualSubsidyCost(s.econ, s.budget), 0.01)

        s.round += 16
        assertTrue(IndustryRules.setEconomicSystem(s, EconomicSystemKey.PROTECTIONIST).ok)
        assertFalse(s.econ.subProvinces["hijaz_jeddah"]!!.factories.first().subsidized)
        assertEquals(0.0, IndustryRules.annualSubsidyCost(s.econ, s.budget), 0.01)

        s.round += 16
        assertTrue(IndustryRules.setEconomicSystem(s, EconomicSystemKey.LAISSEZ_FAIRE).ok)
        assertFalse(IndustryRules.buildOrUpgradeFactory(s, "anatolia_izmir", FactoryKey.TRADE_DEPOT).ok)

        // 5) Frontier conquest on decisive victory & AI conquer option & lost province factory clear & JSON backward compat
        val sf = GameState(ppy = 2, seed = 55, treasury = 100_000_000.0)
        sf.courtActions = 20
        WarRules.declare(sf, "france", byUs = true)
        val w1 = WarRules.conclude(sf, sf.wars.first(), 65.0)
        assertTrue(w1.contains("الجزائر"))
        assertTrue(sf.econ.frontierRegions["algeria"]!!.controlled)
        assertFalse(sf.econ.frontierRegions["tunisia"]!!.controlled)

        WarRules.declare(sf, "france", byUs = true)
        val w2 = WarRules.conclude(sf, sf.wars.first(), 65.0)
        assertTrue(w2.contains("تونس"))
        assertTrue(sf.econ.frontierRegions["tunisia"]!!.controlled)
        assertTrue(ProvinceRules.suppress(sf, "algeria").ok)

        val optConq = DecisionOption(text = "فتح", result = "تم", conquer = "caucasus")
        val dec = Decision(DecisionCategory.FOREIGN, "عنوان", "القوقاز", "مشهد", "معضلة", listOf(optConq, optConq, optConq))
        sf.round = 150 // 1875 -> Baku oil
        sf.plan = RoundPlan("رأس", "ملخص", "", null, listOf(dec, dec, dec))
        sf.picks = mutableListOf(0, 0, 0)
        GameEngine().resolveRound(sf)
        assertTrue(sf.econ.frontierRegions["caucasus"]!!.controlled)
        assertTrue(sf.econ.oilDiscovered.contains("caucasus_baku"))

        val prompt = buildRoundPrompt(sf)
        assertTrue(prompt.contains("اقتصاد: نظام"))
        assertTrue(prompt.contains("الثغور: مفتوحة"))

        assertTrue(IndustryRules.buildOrUpgradeFactory(sf, "egypt_alexandria", FactoryKey.TRADE_DEPOT).ok)
        ProvinceRules.lose(sf, sf.econ.provinces["egypt"]!!, "cession")
        assertTrue(sf.econ.subProvinces["egypt_alexandria"]!!.factories.isEmpty())

        val restored = GameState.fromJson(sf.toJson())
        assertTrue(restored.econ.frontierRegions["caucasus"]!!.controlled)
        assertTrue(restored.econ.oilDiscovered.contains("caucasus_baku"))

        val legacyJson = sf.toJson().apply {
            getJSONObject("econ").apply {
                remove("subProvinces")
                remove("frontierRegions")
                remove("economicSystem")
                remove("lastEconSystemRound")
                remove("oilDiscovered")
            }
        }
        val restoredLegacy = GameState.fromJson(legacyJson)
        assertEquals(EconomicSystemKey.STATE_CAPITALISM, restoredLegacy.econ.economicSystem)
        assertEquals(25, restoredLegacy.econ.subProvinces.size)
        assertFalse(restoredLegacy.econ.frontierRegions["algeria"]!!.controlled)
    }
}

