package kr.sunapse.sunflow

import org.springframework.data.jpa.repository.JpaRepository

interface GeometryRepository : JpaRepository<Geometry, Long> {

    fun findAllByJusoOldContaining(jusoOld : String) : List<Geometry>
}