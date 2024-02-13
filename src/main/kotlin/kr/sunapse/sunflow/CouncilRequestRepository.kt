package kr.sunapse.sunflow

import org.springframework.data.jpa.repository.JpaRepository

interface CouncilRequestRepository : JpaRepository<CouncilRequest, Long> {
}