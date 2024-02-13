package kr.sunapse.sunflow

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class CouncilController(private val councilRequestRepository: CouncilRequestRepository) {

    @PostMapping("/council/new")
    fun councilNew(@RequestBody request: NewCouncilRequest): CouncilRequest {
        return councilRequestRepository.save(CouncilRequest(
                email = request.email,
                infoUrl = request.infoFileUrl,
                modelUrl = request.modelFileUrl
        ))
    }
}

data class NewCouncilRequest(
        val email: String,
        val infoFileUrl: String,
        val modelFileUrl: String
)