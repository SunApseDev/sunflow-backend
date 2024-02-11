package kr.sunapse.sunflow

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.roundToLong

fun Double.rounding(scale: Long = 1): Double {
    if (scale == 0L) {
        return this.roundToInt().toDouble()
    }

    val scaleNumber = (10.0).pow(scale.toDouble())

    return (this * scaleNumber).roundToInt() / scaleNumber
}

@RestController
class GeometryController(private val geometryService: GeometryService) {

    @GetMapping("/", "/hello")
    fun heathCheck(): Boolean {
        return true
    }

    @PostMapping("/geometry/search")
    fun getGeometry(@RequestBody request: GeometrySearchBody): GeometryResponse {

        val geos = geometryService.getByJusoOld(request.jusoOld.trim())

        if (geos.size < 1) {
            return GeometryResponse()
        } else {
            val maxAreaGeo = geos.maxBy { geo -> geo.totArea }
            return GeometryResponse.of(maxAreaGeo)
        }
    }
}


data class GeometrySearchBody(val jusoOld: String)


class GeometryResponse(
        val isInstallRequired: Boolean = false,
        val id: Long,
        val totArea: Double,
        val bldUse: String,
        val jusoOld: String,
        val jusoNew: String,
        val bldName: String,
        val bldH: Double,
        val sedae: Double,
        val maxInstall: InstallInfo,
        val optInstall: InstallInfo,
        val gltfUrl: String = "",
        val requiredRatio: Double,
        val maxEconomics: GeometryEconomicsInfo,
        val optEconomics: GeometryEconomicsInfo
) {

    constructor() : this(isInstallRequired = false,
            id = 0,
            totArea = 0.0,
            bldUse = "",
            jusoOld = "",
            jusoNew = "",
            bldName = "",
            bldH = 0.0,
            sedae = 0.0,
            maxInstall = InstallInfo(),
            optInstall = InstallInfo(),
            gltfUrl = "",
            requiredRatio = 0.0,
            maxEconomics = GeometryEconomicsInfo(),
            optEconomics = GeometryEconomicsInfo()
    )

    companion object {
        fun of(geometry: Geometry): GeometryResponse {

            val bldCategory = BldCategoryChecker.check(geometry.bldUse)

            // A
            val expectedEnergyUse = bldCategory.unitEnegryAmount * geometry.totArea

            // B
            val buildingPurpose = PurposeChecker.checkPurpose(geometry.bldUse)

            val requiredRatio : Double = when(buildingPurpose){
                BuildingPurpose.주거 -> {
                    when {
                        geometry.sedae >= 1000 -> 0.105
                        geometry.sedae >= 300 -> 0.10
                        geometry.sedae >= 30 -> 0.095
                        else -> 0.0
                    }
                }
                BuildingPurpose.비주거 -> {
                    when {
                        geometry.totArea >= 100000.0 -> 0.145
                        geometry.totArea >= 10000.0 -> 0.135
                        geometry.totArea >= 3000.0 -> 0.125
                        else -> 0.0
                    }
                }
                BuildingPurpose.공공 -> {
                    when {
                        geometry.totArea >= 500 -> 0.34
                        else -> 0.0
                    }
                }
                BuildingPurpose.Nothing -> 0.0
            }

            // A * B
            val expectedRequiredEnergyProduce = expectedEnergyUse * requiredRatio
            val optInstallInfo: InstallInfo? = when {
                // A*B < x
                expectedRequiredEnergyProduce < (geometry.pvRGenC ?: 0.0) -> {

                    val pvRCap = round(expectedRequiredEnergyProduce / (1358 * 0.95))
                    InstallInfo(
                            pvRGenC = expectedRequiredEnergyProduce.rounding(),
                            pvRCap = pvRCap.rounding(),
                            pvRArea = (pvRCap * 1.134 * 2.092 / 0.5).rounding(scale = 0),
                            ratioR = (requiredRatio * 100).rounding()
                    )
                }
                // x <= A*B < x+y
                ((geometry.pvRGenC
                        ?: 0.0) <= expectedRequiredEnergyProduce) && (expectedRequiredEnergyProduce < ((geometry.pvRGenC
                        ?: 0.0) + (geometry.pvSGenC ?: 0.0))) -> {

                    val pvSGenC = expectedRequiredEnergyProduce - (geometry.pvRGenC ?: 0.0)
                    val pvSCap = pvSGenC / (923 * 6.12)
                    val pvSArea = (pvSCap * 1.06 * 1.06 / 0.16).rounding(scale = 0) // a2
                    val ratioS = requiredRatio - ((geometry.pvRGenC ?: 0.0) / expectedEnergyUse) // d2

                    InstallInfo(pvSGenC = pvSGenC.rounding(),
                            pvSCap = pvSCap.rounding(),
                            pvSArea = pvSArea.rounding(), // a2
                            ratioS = ratioS.rounding()) // d2

                }

                // x+y <= A*B < x+y+z
                (((geometry.pvRGenC ?: 0.0) + (geometry.pvSGenC
                        ?: 0.0)) <= expectedRequiredEnergyProduce) && (expectedRequiredEnergyProduce < ((geometry.pvRGenC
                        ?: 0.0) + (geometry.pvSGenC ?: 0.0) + (geometry.pvEGenC ?: 0.0))) -> {
                    val pvEGenC = expectedRequiredEnergyProduce - (geometry.pvRGenC ?: 0.0) - (geometry.pvSGenC
                            ?: 0.0)
                    val pvECap = round(pvEGenC / (923 * 6.12))
                    val pEArea = pvECap * 1.06 * 1.06 / 0.16
                    val ratioE = requiredRatio - ((geometry.pvRGenC ?: 0.0) + (geometry.pvSGenC
                            ?: 0.0)) / expectedEnergyUse  // d2

                    InstallInfo(
                            pvEGenC = pvEGenC.rounding(),
                            pvECap = pvECap.rounding(),
                            pvEArea = pEArea.rounding(scale = 0), // a3
                            ratioE = ratioE.rounding(), // d3

                            // a1~d1 최대 설치시와 동일
                            pvRArea = geometry.pvRArea?.rounding() ?: 0.0,
                            pvRCap = geometry.pvRCap?.rounding() ?: 0.0,
                            pvRGenC = geometry.pvRGenC?.rounding() ?: 0.0,
                            ratioR = (((geometry.pvRGenC ?: 0.0) / expectedEnergyUse) * 100).rounding(),

                            // a2~d2 최대 설치시와 동일
                            pvSArea = geometry.pvSArea?.rounding() ?: 0.0,
                            pvSCap = geometry.pvSCap?.rounding() ?: 0.0,
                            pvSGenC = geometry.pvSGenC?.rounding() ?: 0.0,
                            ratioS = (((geometry.pvSGenC ?: 0.0) / expectedEnergyUse) * 100).rounding()
                    )
                }
                //  x+y+z <= A*B < x+y+z+w
                (((geometry.pvRGenC ?: 0.0) + (geometry.pvSGenC ?: 0.0) + (geometry.pvEGenC
                        ?: 0.0)) <= expectedRequiredEnergyProduce) && (expectedRequiredEnergyProduce < ((geometry.pvRGenC
                        ?: 0.0) + (geometry.pvSGenC ?: 0.0) + (geometry.pvEGenC ?: 0.0) + (geometry.pvWGenC
                        ?: 0.0))) -> {
                    val pvWGenC = expectedRequiredEnergyProduce - (geometry.pvRGenC ?: 0.0) - (geometry.pvSGenC
                            ?: 0.0) - (geometry.pvEGenC ?: 0.0)
                    val pvWCap = round(pvWGenC / (923 * 6.12))

                    // a4
                    val pvWArea = pvWCap * 1.06 * 1.06 / 0.16

                    // d4
                    val ratioW = requiredRatio - ((geometry.pvRGenC ?: 0.0) + (geometry.pvSGenC
                            ?: 0.0) + (geometry.pvEGenC ?: 0.0)) / expectedEnergyUse

                    InstallInfo(
                            // a~d1 최대설치시
                            pvRArea = (geometry.pvRArea ?: 0.0).rounding(scale = 0),
                            pvRCap = (geometry.pvRCap ?: 0.0).rounding(),
                            pvRGenC = (geometry.pvRGenC ?: 0.0).rounding(),
                            ratioR = (((geometry.pvRGenC ?: 0.0) / expectedEnergyUse) * 100).rounding(),

                            // a~d2 최대설치시
                            pvSArea = (geometry.pvSArea ?: 0.0).rounding(),
                            pvSCap = (geometry.pvSCap ?: 0.0).rounding(),
                            pvSGenC = (geometry.pvSGenC ?: 0.0).rounding(),
                            ratioS = (((geometry.pvSGenC ?: 0.0) / expectedEnergyUse) * 100).rounding(),

                            // a~d3 최대설치시
                            pvEArea = (geometry.pvEArea ?: 0.0).rounding(),
                            pvECap = (geometry.pvECap ?: 0.0).rounding(),
                            pvEGenC = (geometry.pvEGenC ?: 0.0).rounding(),
                            ratioE = (((geometry.pvEGenC ?: 0.0) / expectedEnergyUse) * 100).rounding(),

                            // a4~d4
                            pvWGenC = pvWGenC.rounding(),
                            pvWCap = pvWCap.rounding(),
                            pvWArea = pvWArea.rounding(scale = 0),
                            ratioW = ratioW.rounding()

                    )
                }

                else -> {
                    // 설치 의무 없음

                    null
                }
            }




            return GeometryResponse(
                    id = geometry.id,
                    totArea = geometry.totArea.rounding(),
                    bldUse = geometry.bldUse,
                    jusoOld = geometry.jusoOld,
                    jusoNew = geometry.jusoNew,
                    bldName = geometry.bldNm,
                    bldH = geometry.bldH.rounding(),
                    sedae = geometry.sedae.rounding(),
                    maxInstall = if (optInstallInfo == null) InstallInfo() else InstallInfo(
                            pvRArea = geometry.pvRArea?.rounding() ?: 0.0,
                            pvSArea = geometry.pvSArea?.rounding() ?: 0.0,
                            pvEArea = geometry.pvEArea?.rounding() ?: 0.0,
                            pvWArea = geometry.pvWArea?.rounding() ?: 0.0,
                            pvRCap = geometry.pvRCap?.rounding() ?: 0.0,
                            pvSCap = geometry.pvSCap?.rounding() ?: 0.0,
                            pvECap = geometry.pvECap?.rounding() ?: 0.0,
                            pvWCap = geometry.pvWCap?.rounding() ?: 0.0,
                            pvRGenC = geometry.pvRGenC?.rounding() ?: 0.0,
                            pvSGenC = geometry.pvSGenC?.rounding() ?: 0.0,
                            pvEGenC = geometry.pvEGenC?.rounding() ?: 0.0,
                            pvWGenC = geometry.pvWGenC?.rounding() ?: 0.0,
                            ratioR = ((geometry.pvRGenC ?: 0.0) / expectedEnergyUse * 100).rounding(),
                            ratioS = ((geometry.pvSGenC ?: 0.0) / expectedEnergyUse * 100).rounding(),
                            ratioE = ((geometry.pvEGenC ?: 0.0) / expectedEnergyUse * 100).rounding(),
                            ratioW = ((geometry.pvWGenC ?: 0.0) / expectedEnergyUse * 100).rounding()
                    ),
                    optInstall = optInstallInfo ?: InstallInfo(),
                    isInstallRequired = optInstallInfo != null,
                    gltfUrl = geometry.gltfUrl ?: "",
                    requiredRatio = (requiredRatio * 100).rounding(),
                    maxEconomics = run {
                        val pv설치용량 = geometry.pvRCap ?: 0.0
                        val pv설치면적 = geometry.pvRArea ?: 0.0
                        val bipv설치용량 = (geometry.pvSCap ?: 0.0) + (geometry.pvECap ?: 0.0) + (geometry.pvWCap ?: 0.0)
                        val bipv설치면적 = (geometry.pvSArea ?: 0.0) + (geometry.pvEArea ?: 0.0) + (geometry.pvWArea ?: 0.0)
                        val bipv설치용량최적 = optInstallInfo?.let { it.pvSCap + it.pvECap + it.pvWCap } ?: 0.0

                        val pvL1 = pv설치용량.roundToLong() * 2000000
                        val bipvL1 = bipv설치용량.roundToLong() * 6500000
                        val bipvL2 = bipv설치면적.roundToLong() * 70_000
                        val bipvL3 = (bipv설치용량 - bipv설치용량최적).roundToLong() * 4_000_000
                        val pvC = pv설치용량 * 3.6 * 365
                        val bipvC = bipv설치용량 * 3.6 * 365
                        val pvSMP = pvC.roundToLong() * 139
                        val bipvSMP = bipvC.roundToLong() * 139
                        val pvREC = (pvC * 82.216 * 1.4).roundToLong()
                        val bipvREC = (bipvC * 82.216 * 1.4).roundToLong()
                        val pvSaving = pvC.roundToLong() * 120
                        val bipvSaving = bipvC.roundToLong() * 120
                        val pvManage = pv설치면적.roundToLong() * 5775
                        val bipvManage = bipv설치면적.roundToLong() * 5775

                        val pvR = pvL1
                        val bipvR = bipvL1 - bipvL2 - bipvL3

                        val pvRevenue = pvSMP + pvREC + pvSaving - pvManage
                        val bipvRevenue = bipvSMP + bipvREC + bipvSaving - bipvManage
                        GeometryEconomicsInfo(
                                pvL1 = pvL1,
                                bipvL1 = bipvL1,
                                bipvL2 = bipvL2,
                                bipvL3 = bipvL3,
                                pvR = pvR,
                                bipvR = bipvR,
                                pvC = pvC,
                                bipvC = bipvC,
                                pvSMP = pvSMP,
                                bipvSMP = bipvSMP,
                                pvREC = pvREC,
                                bipvREC = bipvREC,
                                pvSaving = pvSaving,
                                bipvSaving = bipvSaving,
                                pvManage = pvManage,
                                bipvManage = bipvManage,
                                pvRevenue = pvRevenue,
                                bipvRevenue = bipvRevenue,
                                pvROI = if (pvRevenue > 0) (pvR / pvRevenue) + 1 else 0,
                                bipvROI = if (bipvRevenue > 0) (bipvR / bipvRevenue) + 1 else 0,
                                sumROI = if (pvRevenue + bipvRevenue > 0) (pvR + bipvR) / (pvRevenue + bipvRevenue) + 1 else 0,
                                pvMargin10y = (9.647 * pvRevenue - 0.353 * pvManage - pvR).roundToLong(),
                                bipvMargin10y = (9.647 * bipvRevenue - 0.353 * bipvManage - bipvR).roundToLong(),

                                )
                    },
                    optEconomics = run {
                        val 최적pv설치용량: Double = optInstallInfo?.pvRCap ?: 0.0
                        val 최적pv설치면적 = optInstallInfo?.pvRArea ?: 0.0
                        val 최적bipv설치면적 = optInstallInfo?.let { it.pvSCap + it.pvECap + it.pvWCap } ?: 0.0
                        val 최적bipv설치용량 = optInstallInfo?.let { it.pvSArea + it.pvEArea + it.pvWArea } ?: 0.0

                        val pvL1 = 최적pv설치용량.roundToLong() * 2_000_000
                        val bipvL1 = 최적bipv설치용량.roundToLong() * 6_500_000
                        val bipvL2 = 최적bipv설치면적.roundToLong() * 70_000
                        val pvC = 최적pv설치용량 * 3.6 * 365
                        val bipvC = 최적bipv설치용량 * 3.6 * 365
                        val pvSMP = pvC.roundToLong() * 139
                        val bipvSMP = bipvC.roundToLong() * 139
                        val pvREC = (pvC * 82.216 * 1.4).roundToLong()
                        val bipvREC = (bipvC * 82.216 * 1.4).roundToLong()
                        val pvSaving = (pvC * 120).roundToLong()
                        val bipvSaving = (bipvC * 120).roundToLong()
                        val pvManage = (최적pv설치면적 * 5775).roundToLong()
                        val bipvManage = (최적bipv설치면적 * 5775).roundToLong()
                        val pvRevenue = pvSMP + pvREC + pvSaving - pvManage
                        val bipvRevenue = bipvSMP + bipvREC + bipvSaving - bipvManage
                        val pvR = pvL1
                        val bipvR = bipvL1 - bipvL2
                        GeometryEconomicsInfo(
                                pvL1 = pvL1,
                                bipvL1 = bipvL1,
                                bipvL2 = bipvL2,
                                pvR = pvR,
                                bipvR = bipvR,
                                pvC = pvC,
                                bipvC = bipvC,
                                pvSMP = pvSMP,
                                bipvSMP = bipvSMP,
                                pvREC = pvREC,
                                bipvREC = bipvREC,
                                pvSaving = pvSaving,
                                bipvSaving = bipvSaving,
                                pvManage = pvManage,
                                bipvManage = bipvManage,
                                pvRevenue = pvRevenue,
                                bipvRevenue = bipvRevenue,
                                pvROI = if (pvRevenue > 0) pvR / pvRevenue + 1 else 0,
                                bipvROI = if (bipvRevenue > 0) bipvR / bipvRevenue + 1 else 0,
                                sumROI = if (pvRevenue + bipvRevenue > 0) (pvR + bipvR) / (pvRevenue + bipvRevenue) + 1 else 0,
                                pvMargin10y = (pvRevenue * 9.647 - pvManage * 0.353 - pvR).roundToLong(),
                                bipvMargin10y = (bipvRevenue * 9.647 - bipvManage * 0.353 - bipvR).roundToLong(),
                        )
                    }
            )
        }
    }
}

data class InstallInfo(
        val pvRArea: Double = 0.0,
        val pvSArea: Double = 0.0,
        val pvEArea: Double = 0.0,
        val pvWArea: Double = 0.0,
        val pvRCap: Double = 0.0,
        val pvSCap: Double = 0.0,
        val pvECap: Double = 0.0,
        val pvWCap: Double = 0.0,
        val pvRGenC: Double = 0.0,
        val pvSGenC: Double = 0.0,
        val pvEGenC: Double = 0.0,
        val pvWGenC: Double = 0.0,
        val ratioR: Double = 0.0,
        val ratioS: Double = 0.0,
        val ratioE: Double = 0.0,
        val ratioW: Double = 0.0
)


class GeometryEconomicsInfo(
        val pvL1: Long = 0,
        val bipvL1: Long = 0,

        val bipvL2: Long = 0,

        val bipvL3: Long = 0,

        val pvR: Long = 0,
        val bipvR: Long = 0,

        // 예상 발전량
        val pvC: Double = 0.0,
        val bipvC: Double = 0.0,

        // SMP
        val pvSMP: Long = 0,
        val bipvSMP: Long = 0,

        // REC
        val pvREC: Long = 0,
        val bipvREC: Long = 0,

        // 전력생산절감비
        val pvSaving: Long = 0,
        val bipvSaving: Long = 0,

        // 유지관리비
        val pvManage: Long = 0,
        val bipvManage: Long = 0,
        // 연별수익
        val pvRevenue: Long = 0,
        val bipvRevenue: Long = 0,

        // ROI
        val pvROI: Long = 0,
        val bipvROI: Long = 0,
        val sumROI: Long = 0,

        // 이윤
        val pvMargin10y: Long = 0,
        val bipvMargin10y: Long = 0,
)