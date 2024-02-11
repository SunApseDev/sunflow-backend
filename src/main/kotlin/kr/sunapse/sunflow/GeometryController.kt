package kr.sunapse.sunflow

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt

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
            requiredRatio = 0.0
    )

    companion object {
        fun of(geometry: Geometry): GeometryResponse {
            // A
            val expectedEnergyUse = 230 * geometry.totArea

            // todo : 현재 비주거 케이스만 다뤘음. 공공과 주거에 대해서도 다뤄야함.
            // B
            val requiredRatio: Double = when {
                geometry.totArea >= 100000.0 -> 0.145
                geometry.totArea >= 10000.0 -> 0.135
                geometry.totArea >= 3000.0 -> 0.125
                else -> 0.0
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