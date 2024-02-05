package kr.sunapse.sunflow

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import kotlin.math.round
import kotlin.math.roundToInt

fun Double.setScale(): Double = (this * 100).roundToInt() / 100.0

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
                            pvRGenC = expectedRequiredEnergyProduce.setScale(),
                            pvRCap = pvRCap.setScale(),
                            pvRArea = (pvRCap * 1.134 * 2.092 / 0.5).setScale(),
                            ratioR = (requiredRatio * 100).setScale()
                    )
                }

                ((geometry.pvRGenC
                        ?: 0.0) <= expectedRequiredEnergyProduce) && (expectedRequiredEnergyProduce < ((geometry.pvRGenC
                        ?: 0.0) + (geometry.pvSGenC ?: 0.0))) -> {

                    val pvSGenC = expectedRequiredEnergyProduce - (geometry.pvRGenC ?: 0.0)
                    val pvSCap = pvSGenC / (923 * 6.12)
                    val pvSArea = pvSCap * 1.06 * 1.06 / 0.16
                    val ratioS = requiredRatio * 100 - ((geometry.pvRGenC ?: 0.0) / expectedEnergyUse * 100)

                    InstallInfo(pvSGenC = pvSGenC.setScale(), pvSCap = pvSCap.setScale(), pvSArea = pvSArea.setScale(), ratioS = ratioS.setScale())

                }

                (((geometry.pvRGenC ?: 0.0) + (geometry.pvSGenC
                        ?: 0.0)) <= expectedRequiredEnergyProduce) && (expectedRequiredEnergyProduce < ((geometry.pvRGenC
                        ?: 0.0) + (geometry.pvSGenC ?: 0.0) + (geometry.pvEGenC ?: 0.0))) -> {
                    val pvEGenC = expectedRequiredEnergyProduce - (geometry.pvRGenC ?: 0.0) - (geometry.pvSGenC
                            ?: 0.0)
                    val pvECap = round(pvEGenC / (923 * 6.12))
                    val pEArea = pvECap * 1.06 * 1.06 / 0.16
                    val ratioE = requiredRatio * 100 - ((geometry.pvRGenC ?: 0.0) + (geometry.pvSGenC
                            ?: 0.0)) / expectedEnergyUse * 100

                    InstallInfo(
                            pvEGenC = pvEGenC.setScale(),
                            pvECap = pvECap.setScale(),
                            pvEArea = pEArea.setScale(),
                            ratioE = ratioE.setScale(),

                            pvRArea = geometry.pvRArea?.setScale() ?: 0.0,
                            pvRCap = geometry.pvRCap?.setScale() ?: 0.0,
                            pvRGenC = geometry.pvRGenC?.setScale() ?: 0.0,
                            ratioR = (((geometry.pvRGenC ?: 0.0) / expectedEnergyUse) * 100).setScale(),

                            pvSArea = geometry.pvSArea?.setScale() ?: 0.0,
                            pvSCap = geometry.pvSCap?.setScale() ?: 0.0,
                            pvSGenC = geometry.pvSGenC?.setScale() ?: 0.0,
                            ratioS = (((geometry.pvSGenC ?: 0.0) / expectedEnergyUse) * 100).setScale()
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
                    val ratioW = requiredRatio * 100 - ((geometry.pvRGenC ?: 0.0) + (geometry.pvSGenC
                            ?: 0.0) + (geometry.pvEGenC ?: 0.0)) / expectedEnergyUse * 100

                    InstallInfo(
                            pvRArea = (geometry.pvRArea ?: 0.0).setScale(),
                            pvRCap = (geometry.pvRCap ?: 0.0).setScale(),
                            pvRGenC = (geometry.pvRGenC ?: 0.0).setScale(),
                            ratioR = (((geometry.pvRGenC ?: 0.0) / expectedEnergyUse) * 100).setScale(),

                            pvSArea = (geometry.pvSArea ?: 0.0).setScale(),
                            pvSCap = (geometry.pvSCap ?: 0.0).setScale(),
                            pvSGenC = (geometry.pvSGenC ?: 0.0).setScale(),
                            ratioS = (((geometry.pvSGenC ?: 0.0) / expectedEnergyUse) * 100).setScale(),

                            pvEArea = (geometry.pvEArea ?: 0.0).setScale(),
                            pvECap = (geometry.pvECap ?: 0.0).setScale(),
                            pvEGenC = (geometry.pvEGenC ?: 0.0).setScale(),
                            ratioE = (((geometry.pvEGenC ?: 0.0) / expectedEnergyUse) * 100).setScale(),

                            pvWGenC = pvWGenC.setScale(),
                            pvWCap = pvWCap.setScale(),
                            pvWArea = pvWArea.setScale(),
                            ratioW = ratioW.setScale()

                    )
                }

                else -> {
                    // 설치 의무 없음

                    null
                }
            }





            return GeometryResponse(
                    id = geometry.id,
                    totArea = geometry.totArea.setScale(),
                    bldUse = geometry.bldUse,
                    jusoOld = geometry.jusoOld,
                    jusoNew = geometry.jusoNew,
                    bldName = geometry.bldNm,
                    bldH = geometry.bldH.setScale(),
                    sedae = geometry.sedae.setScale(),
                    maxInstall = if (optInstallInfo == null) InstallInfo() else InstallInfo(
                            pvRArea = geometry.pvRArea?.setScale() ?: 0.0,
                            pvSArea = geometry.pvSArea?.setScale() ?: 0.0,
                            pvEArea = geometry.pvEArea?.setScale() ?: 0.0,
                            pvWArea = geometry.pvWArea?.setScale() ?: 0.0,
                            pvRCap = geometry.pvRCap?.setScale() ?: 0.0,
                            pvSCap = geometry.pvSCap?.setScale() ?: 0.0,
                            pvECap = geometry.pvECap?.setScale() ?: 0.0,
                            pvWCap = geometry.pvWCap?.setScale() ?: 0.0,
                            pvRGenC = geometry.pvRGenC?.setScale() ?: 0.0,
                            pvSGenC = geometry.pvSGenC?.setScale() ?: 0.0,
                            pvEGenC = geometry.pvEGenC?.setScale() ?: 0.0,
                            pvWGenC = geometry.pvWGenC?.setScale() ?: 0.0,
                            ratioR = ((geometry.pvRGenC ?: 0.0) / expectedEnergyUse * 100).setScale(),
                            ratioS = ((geometry.pvSGenC ?: 0.0) / expectedEnergyUse * 100).setScale(),
                            ratioE = ((geometry.pvEGenC ?: 0.0) / expectedEnergyUse * 100).setScale(),
                            ratioW = ((geometry.pvWGenC ?: 0.0) / expectedEnergyUse * 100).setScale()
                    ),
                    optInstall = optInstallInfo ?: InstallInfo(),
                    isInstallRequired = optInstallInfo != null,
                    gltfUrl = geometry.gltfUrl ?: "",
                    requiredRatio = (requiredRatio * 100).setScale(),
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