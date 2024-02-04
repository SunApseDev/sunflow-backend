package kr.sunapse.sunflow

import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@Service
class GeometryService(private val geometryRepository: GeometryRepository) {

    fun getGeometry(id: Long): Geometry? {
        return geometryRepository.findByIdOrNull(id)
    }

    fun getByJusoOld(jusoOld: String): List<Geometry> {
        return geometryRepository.findAllByJusoOldContaining(jusoOld)
    }
}