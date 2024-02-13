package kr.sunapse.sunflow

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "council_request")
class CouncilRequest(
        @Column(name = "email")
        var email : String,
        @Column(name = "info_url")
        var infoUrl : String,
        @Column(name = "model_url")
        var modelUrl : String
) {
    @Column(name = "id")
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0L

    @Column(name = "reg_ts")
    val regTs : LocalDateTime = LocalDateTime.now()

    @Column(name = "upd_ts")
    val updTs : LocalDateTime = LocalDateTime.now()

}