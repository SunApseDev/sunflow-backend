package kr.sunapse.sunflow

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import kotlin.math.round

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
        val gltfUrl: String = ""
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
            gltfUrl = "")

    companion object {
        fun of(geometry: Geometry): GeometryResponse {
            val expectedEnergyUse = 230 * geometry.totArea

            // todo : 현재 비주거 케이스만 다뤘음. 공공과 주거에 대해서도 다뤄야함.
            val requiredRatio: Double = when {
                geometry.totArea >= 100000.0 -> 0.145
                geometry.totArea >= 10000.0 -> 0.135
                geometry.totArea >= 3000.0 -> 0.125
                else -> 0.0
            }

            val expectedRequiredEnergyProduce = expectedEnergyUse * requiredRatio
            val optInstallInfo: InstallInfo? = when {
                expectedRequiredEnergyProduce < (geometry.pvRGenC ?: 0.0) -> {

                    val pvRCap = round(expectedRequiredEnergyProduce / (1358 * 0.95))
                    InstallInfo(
                            pvRGenC = expectedRequiredEnergyProduce,
                            pvRCap = pvRCap,
                            pvRArea = pvRCap * 1.134 * 2.092 / 0.5,
                            ratioR = requiredRatio
                    )
                }

                ((geometry.pvRGenC
                        ?: 0.0) <= expectedRequiredEnergyProduce) && (expectedRequiredEnergyProduce < ((geometry.pvRGenC
                        ?: 0.0) + (geometry.pvSGenC ?: 0.0))) -> {

                    val pvSGenC = expectedRequiredEnergyProduce - (geometry.pvRGenC ?: 0.0)
                    val pvSCap = round(pvSGenC / (923 * 6.12))
                    val pvSArea = pvSCap * 1.06 * 1.06 / 0.16
                    val ratioS = requiredRatio - ((geometry.pvRGenC ?: 0.0) / expectedEnergyUse * 100)

                    InstallInfo(pvSGenC = pvSGenC, pvSCap = pvSCap, pvSArea = pvSArea, ratioS = ratioS)

                }

                (((geometry.pvRGenC ?: 0.0) + (geometry.pvSGenC
                        ?: 0.0)) <= expectedRequiredEnergyProduce) && (expectedRequiredEnergyProduce < ((geometry.pvRGenC
                        ?: 0.0) + (geometry.pvSGenC ?: 0.0) + (geometry.pvEGenC ?: 0.0))) -> {
                    val pvEGenC = expectedRequiredEnergyProduce - (geometry.pvRGenC ?: 0.0) - (geometry.pvSGenC
                            ?: 0.0)
                    val pvECap = round(pvEGenC / (923 * 6.12))
                    val pEArea = pvECap * 1.06 * 1.06 / 0.16
                    val ratioE = requiredRatio - ((geometry.pvRGenC ?: 0.0) + (geometry.pvSGenC
                            ?: 0.0)) / expectedEnergyUse * 100

                    InstallInfo(
                            pvEGenC = pvEGenC,
                            pvECap = pvECap,
                            pvEArea = pEArea,
                            ratioE = ratioE,

                            pvRArea = geometry.pvRArea ?: 0.0,
                            pvRCap = geometry.pvRCap ?: 0.0,
                            pvRGenC = geometry.pvRGenC ?: 0.0,
                            ratioR = ((geometry.pvRGenC ?: 0.0) / expectedEnergyUse) * 100,

                            pvSArea = geometry.pvSArea ?: 0.0,
                            pvSCap = geometry.pvSCap ?: 0.0,
                            pvSGenC = geometry.pvSGenC ?: 0.0,
                            ratioS = ((geometry.pvSGenC ?: 0.0) / expectedEnergyUse) * 100
                    )
                }

                (((geometry.pvRGenC ?: 0.0) + (geometry.pvSGenC ?: 0.0) + (geometry.pvEGenC
                        ?: 0.0)) <= expectedRequiredEnergyProduce) && (expectedRequiredEnergyProduce < ((geometry.pvRGenC
                        ?: 0.0) + (geometry.pvSGenC ?: 0.0) + (geometry.pvEGenC ?: 0.0) + (geometry.pvWGenC
                        ?: 0.0))) -> {
                    val pvWGenC = expectedRequiredEnergyProduce - (geometry.pvRGenC ?: 0.0) - (geometry.pvSGenC
                            ?: 0.0) - (geometry.pvEGenC ?: 0.0) - (geometry.pvWGenC ?: 0.0)
                    val pvWCap = round(pvWGenC / (923 * 6.12))
                    val pvWArea = pvWCap * 1.06 * 1.06 / 0.16
                    val ratioW = requiredRatio - ((geometry.pvRGenC ?: 0.0) + (geometry.pvSGenC
                            ?: 0.0) + (geometry.pvEGenC ?: 0.0)) / expectedEnergyUse * 100

                    InstallInfo(
                            pvRArea = (geometry.pvRArea ?: 0.0),
                            pvRCap = (geometry.pvRCap ?: 0.0),
                            pvRGenC = (geometry.pvRGenC ?: 0.0),
                            ratioR = ((geometry.pvRGenC ?: 0.0) / expectedEnergyUse) * 100,

                            pvSArea = (geometry.pvSArea ?: 0.0),
                            pvSCap = geometry.pvSCap ?: 0.0,
                            pvSGenC = geometry.pvSGenC ?: 0.0,
                            ratioS = ((geometry.pvSGenC ?: 0.0) / expectedEnergyUse) * 100,

                            pvEArea = (geometry.pvEArea ?: 0.0),
                            pvECap = (geometry.pvECap ?: 0.0),
                            pvEGenC = (geometry.pvEGenC ?: 0.0),
                            ratioE = ((geometry.pvEGenC ?: 0.0) / expectedEnergyUse) * 100,

                            pvWGenC = pvWGenC,
                            pvWCap = pvWCap,
                            pvWArea = pvWArea,
                            ratioW = ratioW

                    )
                }

                else -> {
                    // 설치 의무 없음

                    null
                }
            }





            return GeometryResponse(
                    id = geometry.id,
                    totArea = geometry.totArea,
                    bldUse = geometry.bldUse,
                    jusoOld = geometry.jusoOld,
                    jusoNew = geometry.jusoNew,
                    bldName = geometry.bldNm,
                    bldH = geometry.bldH,
                    sedae = geometry.sedae,
                    maxInstall = if (optInstallInfo == null) InstallInfo() else InstallInfo(
                            pvRArea = geometry.pvRArea ?: 0.0,
                            pvSArea = geometry.pvSArea ?: 0.0,
                            pvEArea = geometry.pvEArea ?: 0.0,
                            pvWArea = geometry.pvWArea ?: 0.0,
                            pvRCap = geometry.pvRCap ?: 0.0,
                            pvSCap = geometry.pvSCap ?: 0.0,
                            pvECap = geometry.pvECap ?: 0.0,
                            pvWCap = geometry.pvWCap ?: 0.0,
                            pvRGenC = geometry.pvRGenC ?: 0.0,
                            pvSGenC = geometry.pvSGenC ?: 0.0,
                            pvEGenC = geometry.pvEGenC ?: 0.0,
                            pvWGenC = geometry.pvWGenC ?: 0.0,
                            ratioR = (geometry.pvRGenC ?: 0.0) / expectedEnergyUse * 100,
                            ratioS = (geometry.pvSGenC ?: 0.0) / expectedEnergyUse * 100,
                            ratioE = (geometry.pvEGenC ?: 0.0) / expectedEnergyUse * 100,
                            ratioW = (geometry.pvWGenC ?: 0.0) / expectedEnergyUse * 100
                    ),
                    optInstall = optInstallInfo ?: InstallInfo(),
                    isInstallRequired = optInstallInfo != null,
                    gltfUrl = geometry.gltfUrl ?: ""
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